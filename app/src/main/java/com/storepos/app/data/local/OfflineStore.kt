package com.storepos.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.storepos.app.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class OfflineStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "motopos_offline.db",
    null,
    2
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            create table cache_blob(
              cache_key text primary key,
              payload text not null,
              updated_at integer not null
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            create table pending_sales(
              id text primary key,
              shop_id text not null,
              payload text not null,
              created_at integer not null,
              last_error text,
              review_state text not null default 'queued'
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("alter table pending_sales add column shop_id text")
            db.execSQL("alter table pending_sales add column review_state text not null default 'needs_review'")
            // Pre-v1.8.0 offline sales may predate the Sherine Store catalog
            // reset. Keep all original payloads but NEVER replay them blindly.
            // A manager can reconcile and re-enter verified sales manually.
            db.query("pending_sales", arrayOf("id", "payload"), null, null, null, null, null)
                .use { cursor ->
                    while (cursor.moveToNext()) {
                        val shop = runCatching {
                            json.decodeFromString<OfflineSalePayload>(cursor.getString(1)).shopId
                        }.getOrNull()
                        db.update(
                            "pending_sales",
                            ContentValues().apply {
                                if (shop != null) put("shop_id", shop)
                                put("review_state", "needs_review")
                                put("last_error", "Legacy sale requires manager review after database reset.")
                            },
                            "id=?",
                            arrayOf(cursor.getString(0))
                        )
                    }
                }
        }
    }

    private fun putBlob(key: String, payload: String) {
        writableDatabase.insertWithOnConflict(
            "cache_blob",
            null,
            ContentValues().apply {
                put("cache_key", key)
                put("payload", payload)
                put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun getBlob(key: String): String? =
        readableDatabase.query(
            "cache_blob",
            arrayOf("payload"),
            "cache_key=?",
            arrayOf(key),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    fun saveProducts(shopId: String, products: List<Product>) =
        putBlob("products:$shopId", json.encodeToString(products))

    /**
     * Require at least one successful online catalog refresh after migration
     * before permitting cash sales offline. Older pre-reset cache alone is
     * not an authoritative product catalog.
     */
    fun markProductCacheTrusted(shopId: String) =
        putBlob("trusted_products:$shopId", System.currentTimeMillis().toString())

    fun isProductCacheTrusted(shopId: String): Boolean =
        getBlob("trusted_products:$shopId") != null

    fun loadProducts(shopId: String): List<Product> =
        getBlob("products:$shopId")?.let {
            runCatching { json.decodeFromString<List<Product>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

    fun saveCustomers(shopId: String, customers: List<Customer>) =
        putBlob("customers:$shopId", json.encodeToString(customers))

    fun loadCustomers(shopId: String): List<Customer> =
        getBlob("customers:$shopId")?.let {
            runCatching { json.decodeFromString<List<Customer>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

    fun saveMotorcycles(shopId: String, motorcycles: List<Motorcycle>) =
        putBlob("motorcycles:$shopId", json.encodeToString(motorcycles))

    fun loadMotorcycles(shopId: String): List<Motorcycle> =
        getBlob("motorcycles:$shopId")?.let {
            runCatching { json.decodeFromString<List<Motorcycle>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

    fun enqueueSale(payload: OfflineSalePayload) {
        writableDatabase.insertWithOnConflict(
            "pending_sales",
            null,
            ContentValues().apply {
                put("id", payload.clientKey)
                put("shop_id", payload.shopId)
                put("review_state", "queued")
                put("payload", json.encodeToString(payload))
                put("created_at", System.currentTimeMillis())
                putNull("last_error")
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    /** A cashier must never sync pending transactions belonging to another shop. */
    fun pendingSales(shopId: String): List<PendingOfflineSale> =
        readSales(shopId, "queued")

    fun needsReview(shopId: String): List<PendingOfflineSale> =
        readSales(shopId, "needs_review")

    private fun readSales(shopId: String, status: String): List<PendingOfflineSale> =
        readableDatabase.query(
            "pending_sales",
            arrayOf("id", "payload", "created_at", "last_error"),
            "shop_id=? and review_state=?",
            arrayOf(shopId, status),
            null,
            null,
            "created_at asc"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val payload = runCatching {
                        json.decodeFromString<OfflineSalePayload>(cursor.getString(1))
                    }.getOrNull() ?: continue
                    add(
                        PendingOfflineSale(
                            id = cursor.getString(0),
                            payload = payload,
                            createdAt = cursor.getLong(2),
                            lastError = cursor.getString(3)
                        )
                    )
                }
            }
        }

    fun markNeedsReview(shopId: String, id: String, reason: String) {
        writableDatabase.update(
            "pending_sales",
            ContentValues().apply {
                put("review_state", "needs_review")
                put("last_error", reason.take(500))
            },
            "id=? and shop_id=?",
            arrayOf(id, shopId)
        )
    }

    fun removePendingSale(shopId: String, id: String) {
        writableDatabase.delete("pending_sales", "id=? and shop_id=?", arrayOf(id, shopId))
    }

    fun setPendingError(shopId: String, id: String, error: String?) {
        writableDatabase.update(
            "pending_sales",
            ContentValues().apply { put("last_error", error?.take(500)) },
            "id=? and shop_id=?",
            arrayOf(id, shopId)
        )
    }
}
