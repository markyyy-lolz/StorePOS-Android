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
    4
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
        createOfflineReceiptTable(db)
        createOfflineInventoryTable(db)
    }

    private fun createOfflineInventoryTable(db: SQLiteDatabase) {
        db.execSQL(
            """create table if not exists pending_inventory(
              id text primary key,
              shop_id text not null,
              actor_id text not null,
              payload text not null,
              created_at integer not null,
              last_error text,
              review_state text not null default 'queued'
            )""".trimIndent()
        )
        db.execSQL("create index if not exists idx_inv_shop on pending_inventory(shop_id,review_state,created_at)")
    }

    private fun createOfflineReceiptTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            create table if not exists offline_receipts(
              client_key text primary key,
              shop_id text not null,
              receipt_number text not null,
              paper_width integer not null,
              thermal_bytes blob not null,
              created_at integer not null
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
        if (oldVersion < 3) createOfflineReceiptTable(db)
        if (oldVersion < 4) createOfflineInventoryTable(db)
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
    /**
     * The catalog and verified server epoch must be committed atomically. A
     * restart mid-download or old pre-reset cache cannot become trusted.
     * Mutating the UI's optimistic quantity cache NEVER changes its epoch.
     */
    fun saveTrustedProducts(shopId: String, products: List<Product>, epoch: String) {
        require(shopId.isNotBlank() && epoch.matches(Regex("^[0-9a-fA-F-]{36}$"))) {
            "Cloud catalog generation missing: offline checkout is disabled."
        }
        val db=writableDatabase
        db.beginTransaction()
        try {
            val now=System.currentTimeMillis()
            for ((key,value) in listOf(
                "products:$shopId" to json.encodeToString(products),
                "catalog_epoch:$shopId" to epoch
            )) {
                db.insertWithOnConflict(
                    "cache_blob",null,
                    ContentValues().apply {
                        put("cache_key",key)
                        put("payload",value)
                        put("updated_at",now)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun cachedCatalogEpoch(shopId: String): String? =
        getBlob("catalog_epoch:$shopId")?.takeIf {
            it.matches(Regex("^[0-9a-fA-F-]{36}$"))
        }

    // The v1.8.0 marker does not count: only a confirmed server epoch may
    // permit an offline checkout after Sherine Store's product reset.
    fun isProductCacheTrusted(shopId: String): Boolean =
        cachedCatalogEpoch(shopId) != null

    /** Mutation plus optimistic catalog snapshot must commit together. */
    fun enqueueInventory(ops: List<OfflineInventoryOperation>, nextProducts: List<Product>) {
        require(ops.isNotEmpty() && ops.map { it.id }.distinct().size == ops.size)
        val shop=ops.first().shopId
        val actor=ops.first().actorId
        val epoch=cachedCatalogEpoch(shop) ?: error("Connect once to verify product catalog before offline inventory.")
        require(ops.all { it.shopId==shop && it.actorId==actor &&
            it.catalogEpoch==epoch && it.id.isNotBlank() }) { "Different shop/operator in offline inventory batch." }
        val db=writableDatabase
        db.beginTransaction()
        try {
            val timestamp=System.currentTimeMillis()
            for (op in ops) {
                db.insertOrThrow("pending_inventory",null,ContentValues().apply {
                    put("id",op.id);put("shop_id",op.shopId);put("actor_id",op.actorId)
                    put("payload",json.encodeToString(op));put("created_at",timestamp)
                    put("review_state","queued")
                })
            }
            db.insertWithOnConflict("cache_blob",null,ContentValues().apply {
                put("cache_key","products:$shop")
                put("payload",json.encodeToString(nextProducts))
                put("updated_at",timestamp)
            },SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun pendingInventory(shopId:String):List<PendingOfflineInventoryOperation> =
        readInventory(shopId,"queued")

    fun needsReviewInventory(shopId:String):List<PendingOfflineInventoryOperation> =
        readInventory(shopId,"needs_review")

    private fun readInventory(shopId:String,status:String):List<PendingOfflineInventoryOperation> =
        readableDatabase.query(
            "pending_inventory",arrayOf("payload","created_at","last_error"),
            "shop_id=? and review_state=?",arrayOf(shopId,status),
            null,null,"created_at asc,rowid asc"
        ).use { c ->
            buildList {
                while(c.moveToNext()) {
                    val item=runCatching {
                        json.decodeFromString<OfflineInventoryOperation>(c.getString(0))
                    }.getOrNull() ?: continue
                    add(PendingOfflineInventoryOperation(item,c.getLong(1),c.getString(2)))
                }
            }
        }

    fun markInventoryReview(shopId:String,opId:String,reason:String) {
        writableDatabase.update("pending_inventory",ContentValues().apply {
            put("review_state","needs_review")
            put("last_error",reason.take(500))
        },"shop_id=? and id=?",arrayOf(shopId,opId))
    }

    fun setInventoryError(shopId:String,opId:String,reason:String) {
        writableDatabase.update("pending_inventory",ContentValues().apply {
            put("last_error",reason.take(500))
        },"shop_id=? and id=?",arrayOf(shopId,opId))
    }

    fun removePendingInventory(shopId:String,opId:String) {
        writableDatabase.delete("pending_inventory","shop_id=? and id=?",arrayOf(shopId,opId))
    }

    fun saveCategories(shopId:String,categories:List<ProductCategory>) =
        putBlob("categories:$shopId",json.encodeToString(categories))

    fun loadCategories(shopId:String):List<ProductCategory> =
        getBlob("categories:$shopId")?.let {
            runCatching { json.decodeFromString<List<ProductCategory>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

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

    /** Both sale and original printable bytes commit together before the cart is cleared. */
    fun enqueueSaleWithReceipt(
        payload: OfflineSalePayload,
        receiptNumber: String,
        paperWidth: Int,
        thermalBytes: ByteArray
    ) {
        require(thermalBytes.isNotEmpty()) { "Cannot queue a cash sale without a local receipt." }
        require(paperWidth == 58 || paperWidth == 80) { "Invalid receipt width." }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            val saleAdded = db.insertWithOnConflict(
                "pending_sales",
                null,
                ContentValues().apply {
                    put("id", payload.clientKey)
                    put("shop_id", payload.shopId)
                    put("review_state", "queued")
                    put("payload", json.encodeToString(payload))
                    put("created_at", now)
                    putNull("last_error")
                },
                SQLiteDatabase.CONFLICT_IGNORE
            )
            // Reusing a client key must not overwrite the original saved sale/receipt.
            if (saleAdded == -1L) {
                val saved = receiptBytes(payload.shopId, payload.clientKey, db)
                require(saved != null && saved.contentEquals(thermalBytes)) {
                    "Original offline receipt differs from this transaction; manager review required."
                }
            } else {
                db.insertOrThrow(
                    "offline_receipts",
                    null,
                    ContentValues().apply {
                        put("client_key", payload.clientKey)
                        put("shop_id", payload.shopId)
                        put("receipt_number", receiptNumber)
                        put("paper_width", paperWidth)
                        put("thermal_bytes", thermalBytes)
                        put("created_at", now)
                    }
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun receiptBytes(shopId: String, clientKey: String): ByteArray? =
        receiptBytes(shopId, clientKey, readableDatabase)

    fun receiptWidth(shopId: String, clientKey: String): Int? =
        readableDatabase.query(
            "offline_receipts", arrayOf("paper_width"),
            "shop_id=? and client_key=?", arrayOf(shopId, clientKey),
            null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else null
        }

    private fun receiptBytes(shopId: String, clientKey: String, db: SQLiteDatabase): ByteArray? =
        db.query(
            "offline_receipts", arrayOf("thermal_bytes"),
            "shop_id=? and client_key=?", arrayOf(shopId, clientKey),
            null, null, null
        ).use { cur -> if (cur.moveToFirst()) cur.getBlob(0) else null }

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
