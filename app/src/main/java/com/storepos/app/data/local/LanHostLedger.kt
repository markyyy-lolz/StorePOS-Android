package com.storepos.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import com.storepos.app.data.model.OfflineSalePayload
import com.storepos.app.data.model.Product
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * A single SQLite authority for offline cash checkout while two StorePOS tablets
 * can still reach one another over the same local Wi-Fi.
 *
 * Works WITH the existing v1.8.2 schema v4, offline receipt archive, trusted
 * catalog epoch, reviewed queue and cashier-specific hybrid replay. No downgrade.
 */
object LanHostLedger {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun accept(
        context: Context,
        payload: OfflineSalePayload,
        receiptNumber: String,
        paperWidth: Int,
        receiptBytes: ByteArray
    ): Map<String, Double> {
        require(payload.clientKey.isNotBlank() && payload.shopId.isNotBlank())
        require(payload.payments.isNotEmpty() && payload.payments.all { it.method == "cash" }) {
            "Only cash checkout is supported while disconnected from StorePOS Cloud."
        }
        require(payload.cashierId != null && payload.catalogEpoch != null) {
            "Offline cashier and product catalog authorization missing."
        }
        require(paperWidth in listOf(58, 80) && receiptBytes.isNotEmpty()) {
            "Valid offline receipt bytes are required."
        }
        require(receiptBytes.size <= 750_000) { "Receipt exceeds LAN limit." }
        val store = OfflineStore(context)
        try {
            val db = store.writableDatabase
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS lan_seen_sales (" +
                    "id TEXT PRIMARY KEY, shop_id TEXT NOT NULL, digest TEXT NOT NULL, created_at INTEGER NOT NULL)"
            )
            val fingerprint = json.encodeToString(payload) + "|" + receiptNumber + "|" + paperWidth +
                "|" + Base64.encodeToString(receiptBytes, Base64.NO_WRAP)
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(fingerprint.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            db.beginTransaction()
            try {
                val trustedEpoch = store.cachedCatalogEpoch(payload.shopId)
                require(trustedEpoch != null && trustedEpoch == payload.catalogEpoch) {
                    "Catalog generation changed; reconnect and revalidate inventory."
                }
                require(store.pendingInventory(payload.shopId).isEmpty() &&
                    store.needsReviewInventory(payload.shopId).isEmpty()) {
                    "Unreconciled inventory operations must be reviewed before LAN selling."
                }
                val seen = db.query(
                    "lan_seen_sales", arrayOf("digest"), "id=? AND shop_id=?",
                    arrayOf(payload.clientKey, payload.shopId), null, null, null
                ).use { cur -> if (cur.moveToFirst()) cur.getString(0) else null }
                if (seen != null) {
                    require(seen == digest) { "Sale key was reused for a different cart or receipt." }
                } else {
                    val catalog = store.loadProducts(payload.shopId)
                    require(catalog.isNotEmpty()) { "Host catalog is empty; refresh online first." }
                    val lines = payload.items
                    require(lines.isNotEmpty() && lines.all { it.quantity.isFinite() && it.quantity > 0 }) {
                        "Invalid sale quantities."
                    }
                    val deductions = lines.groupBy { it.productId }
                        .mapValues { (_, rows) -> rows.sumOf { it.quantity } }
                    deductions.forEach { (id, sold) ->
                        val p = catalog.firstOrNull { it.id == id }
                            ?: error("Item not in host's trusted catalog.")
                        require(p.isActive) { "Inactive item cannot be sold." }
                        require(!p.isWeighed && !p.batchTracked && !p.serialTracked &&
                            p.retailParentId == null && p.wholesaleMin <= 0) {
                            "Special retail items require online verification."
                        }
                        if (p.trackStock) {
                            require(p.stockQuantity + 0.000001 >= sold) {
                                "Not enough shared stock for " + p.name + ": " + p.stockQuantity + " left."
                            }
                        }
                    }
                    val adjusted = catalog.map { p ->
                        val sold = deductions[p.id] ?: 0.0
                        if (p.trackStock && sold > 0.0)
                            p.copy(stockQuantity = (p.stockQuantity - sold).coerceAtLeast(0.0))
                        else p
                    }
                    val now = System.currentTimeMillis()
                    db.insertOrThrow("pending_sales", null, ContentValues().apply {
                        put("id", payload.clientKey)
                        put("shop_id", payload.shopId)
                        put("review_state", "queued")
                        put("payload", json.encodeToString(payload))
                        put("created_at", now)
                        putNull("last_error")
                    })
                    db.insertOrThrow("offline_receipts", null, ContentValues().apply {
                        put("client_key", payload.clientKey)
                        put("shop_id", payload.shopId)
                        put("receipt_number", receiptNumber)
                        put("paper_width", paperWidth)
                        put("thermal_bytes", receiptBytes)
                        put("created_at", now)
                    })
                    db.insertWithOnConflict(
                        "cache_blob", null,
                        ContentValues().apply {
                            put("cache_key", "products:" + payload.shopId)
                            put("payload", json.encodeToString(adjusted))
                            put("updated_at", now)
                        }, SQLiteDatabase.CONFLICT_REPLACE
                    )
                    db.insertOrThrow("lan_seen_sales", null, ContentValues().apply {
                        put("id", payload.clientKey)
                        put("shop_id", payload.shopId)
                        put("digest", digest)
                        put("created_at", now)
                    })
                }
                val result = store.loadProducts(payload.shopId)
                    .associate { it.id to it.stockQuantity }
                db.setTransactionSuccessful()
                return result
            } finally {
                db.endTransaction()
            }
        } finally {
            store.close()
        }
    }
}
