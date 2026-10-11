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
              payload text not null,
              created_at integer not null,
              last_error text
            )
            """.trimIndent()
        )
        createLanSeenTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Non-destructive migration: preserve all existing queues, products and customers.
        if (oldVersion < 2) createLanSeenTable(db)
    }

    private fun createLanSeenTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS lan_seen_sales (" +
                "id TEXT PRIMARY KEY, shop_id TEXT NOT NULL, created_at INTEGER NOT NULL)"
        )
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

    fun loadProducts(shopId: String): List<Product> =
        getBlob("products:$shopId")?.let {
            runCatching { json.decodeFromString<List<Product>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

    /**
     * Reconcile a newly downloaded cloud catalog WITH any still-pending local
     * sales under one write lock. This prevents cloud refresh from clobbering
     * an offline LAN sale which arrived during the network request.
     */
    fun saveCloudProductsWithPending(shopId: String, remote: List<Product>): List<Product> {
        val database = writableDatabase
        database.beginTransaction()
        try {
            val reserved = pendingSales().filter { it.payload.shopId == shopId }
                .flatMap { it.payload.items }
                .groupBy { it.productId }
                .mapValues { (_, lines) -> lines.sumOf { it.quantity } }
            val adjusted = remote.map { item ->
                val quantity = reserved[item.id] ?: 0.0
                if (quantity > 0 && item.trackStock) item.copy(
                    stockQuantity = (item.stockQuantity - quantity).coerceAtLeast(0.0)
                ) else item
            }
            saveProducts(shopId, adjusted)
            database.setTransactionSuccessful()
            return adjusted
        } finally {
            database.endTransaction()
        }
    }

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
                put("payload", json.encodeToString(payload))
                put("created_at", System.currentTimeMillis())
                putNull("last_error")
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    /**
     * Keep local pending sale and local stock snapshot in the same transaction.
     * If saving stock fails, no "successful" checkout is accepted.
     */
    fun enqueueSaleWithProducts(payload: OfflineSalePayload, products: List<Product>) {
        val database = writableDatabase
        database.beginTransaction()
        try {
            val alreadySaved = database.query(
                "pending_sales", arrayOf("id"), "id=?",
                arrayOf(payload.clientKey), null, null, null
            ).use { it.moveToFirst() }
            if (!alreadySaved) {
                // Always re-read DB INSIDE transaction. UI snapshots can be stale
                // when another coroutine saves a checkout at the same time.
                val actual = loadProducts(payload.shopId).ifEmpty { products }
                require(actual.isNotEmpty()) { "No cached stock; go online to load products." }
                val grouped = payload.items.groupBy { it.productId }
                    .mapValues { (_, lines) -> lines.sumOf { it.quantity } }
                require(grouped.isNotEmpty() && payload.items.all {
                    it.quantity.isFinite() && it.quantity > 0.0
                }) { "Invalid quantity in sale." }
                grouped.forEach { (id, quantity) ->
                    val item = actual.firstOrNull { it.id == id }
                        ?: error("Product not present in offline catalog.")
                    require(item.isActive) { "Item is no longer active." }
                    if (item.trackStock) require(item.stockQuantity + 0.000001 >= quantity) {
                        "Not enough offline stock for " + item.name +
                            ": only " + item.stockQuantity + " left."
                    }
                }
                val adjusted = actual.map { p ->
                    val sold = grouped[p.id] ?: 0.0
                    if (p.trackStock && sold > 0.0) {
                        p.copy(stockQuantity = (p.stockQuantity - sold).coerceAtLeast(0.0))
                    } else p
                }
                enqueueSale(payload)
                saveProducts(payload.shopId, adjusted)
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    /**
     * A LAN-hosted sale is atomically checked against the HOST's last cached stock,
     * saved to its durable outbox and deducted once. All POS devices send requests
     * through the same database transaction, so two tablets cannot both claim
     * the final unit while the host is reachable.
     */
    fun acceptLanSale(payload: OfflineSalePayload): Map<String, Double> {
        val database = writableDatabase
        database.beginTransaction()
        try {
            val raw = database.query(
                "cache_blob", arrayOf("payload"), "cache_key=?",
                arrayOf("products:" + payload.shopId), null, null, null
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?: error("Host inventory not cached. Load this shop online on the host first.")
            val catalog = json.decodeFromString<List<Product>>(raw)
            require(catalog.isNotEmpty()) { "No local product catalog exists for this shop." }
            val known = database.query(
                "lan_seen_sales", arrayOf("id"), "id=? AND shop_id=?",
                arrayOf(payload.clientKey, payload.shopId), null, null, null
            ).use { it.moveToFirst() }
            if (!known) {
                val grouped = payload.items.groupBy { it.productId }
                    .mapValues { (_, rows) -> rows.sumOf { it.quantity } }
                require(grouped.isNotEmpty() && payload.items.all { it.quantity > 0.0 && it.quantity.isFinite() }) {
                    "Invalid sale items."
                }
                val byId = catalog.associateBy { it.id }
                grouped.forEach { (productId, quantity) ->
                    val product = byId[productId] ?: error("Product missing from host catalog.")
                    require(product.isActive) { "A product in this sale is inactive." }
                    require(!product.serialTracked && !product.batchTracked &&
                        product.retailParentId == null && product.wholesaleMin <= 0) {
                        "Advanced stock validation requires StorePOS Cloud."
                    }
                    if (product.trackStock) {
                        require(product.stockQuantity + 0.000001 >= quantity) {
                            "Stock conflict: " + product.name + " has only " + product.stockQuantity + " remaining on LAN host."
                        }
                    }
                }
                // Debit store credit under the SAME transaction lock as stock.
                // Cashier cannot exceed the host's most recently cached balance.
                val spentCredit = payload.payments.filter { it.method == "store_credit" }
                    .sumOf { it.amount }
                if (spentCredit > 0) {
                    val customerId = payload.customerId
                        ?: error("Offline store credit requires a selected customer.")
                    val customers = loadCustomers(payload.shopId)
                    val account = customers.firstOrNull { it.id == customerId && it.isActive }
                        ?: error("Customer credit record not cached on LAN host.")
                    require(account.storeCreditBalance + 0.000001 >= spentCredit) {
                        "Insufficient shared store-credit balance for " + account.name + "."
                    }
                    saveCustomers(payload.shopId, customers.map { customer ->
                        if (customer.id == customerId) customer.copy(
                            storeCreditBalance = (customer.storeCreditBalance - spentCredit).coerceAtLeast(0.0)
                        ) else customer
                    })
                }
                require(payload.payments.none { it.method in setOf("credit", "paymongo") }) {
                    "PayMongo or new customer debt cannot be finalized offline."
                }

                val updated = catalog.map { product ->
                    val deduction = grouped[product.id] ?: 0.0
                    if (product.trackStock && deduction > 0) {
                        product.copy(stockQuantity = (product.stockQuantity - deduction).coerceAtLeast(0.0))
                    } else product
                }
                database.insertOrThrow(
                    "lan_seen_sales", null,
                    ContentValues().apply {
                        put("id", payload.clientKey)
                        put("shop_id", payload.shopId)
                        put("created_at", System.currentTimeMillis())
                    }
                )
                database.insertOrThrow(
                    "pending_sales", null,
                    ContentValues().apply {
                        put("id", payload.clientKey)
                        put("payload", json.encodeToString(payload))
                        put("created_at", System.currentTimeMillis())
                        putNull("last_error")
                    }
                )
                database.insertWithOnConflict(
                    "cache_blob", null,
                    ContentValues().apply {
                        put("cache_key", "products:" + payload.shopId)
                        put("payload", json.encodeToString(updated))
                        put("updated_at", System.currentTimeMillis())
                    }, SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            val result = database.query(
                "cache_blob", arrayOf("payload"), "cache_key=?",
                arrayOf("products:" + payload.shopId), null, null, null
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?: error("Host inventory snapshot unavailable.")
            val counts = json.decodeFromString<List<Product>>(result)
                .associate { it.id to it.stockQuantity }
            database.setTransactionSuccessful()
            return counts
        } finally {
            database.endTransaction()
        }
    }

    /** Save an acknowledged LAN sale with host-authoritative inventory numbers. */
    fun saveLanAcknowledgedSale(payload: OfflineSalePayload, authoritative: Map<String, Double>) {
        val database = writableDatabase
        database.beginTransaction()
        try {
            val cache = loadProducts(payload.shopId)
            require(cache.isNotEmpty()) { "Missing local inventory catalog." }
            val updated = cache.map { item ->
                authoritative[item.id]?.let { newStock ->
                    item.copy(stockQuantity = newStock)
                } ?: item
            }
            enqueueSale(payload)
            saveProducts(payload.shopId, updated)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun lanCreditBalances(shopId: String): Map<String, Double> =
        loadCustomers(shopId).associate { it.id to it.storeCreditBalance }

    fun applyLanCreditBalances(shopId: String, balances: Map<String, Double>): List<Customer> {
        val database = writableDatabase
        database.beginTransaction()
        try {
            val changed = loadCustomers(shopId).map { customer ->
                balances[customer.id]?.let { updated ->
                    customer.copy(storeCreditBalance = updated)
                } ?: customer
            }
            saveCustomers(shopId, changed)
            database.setTransactionSuccessful()
            return changed
        } finally {
            database.endTransaction()
        }
    }

    /** Apply the LAN hub's stock snapshot without overwriting product metadata. */
    fun applyLanStock(shopId: String, authoritative: Map<String, Double>): List<Product> {
        val database = writableDatabase
        database.beginTransaction()
        try {
            val catalog = loadProducts(shopId)
            val adjusted = catalog.map { product ->
                authoritative[product.id]?.let {
                    product.copy(stockQuantity = it)
                } ?: product
            }
            saveProducts(shopId, adjusted)
            database.setTransactionSuccessful()
            return adjusted
        } finally {
            database.endTransaction()
        }
    }

    fun pendingSales(): List<PendingOfflineSale> =
        readableDatabase.query(
            "pending_sales",
            arrayOf("id", "payload", "created_at", "last_error"),
            null,
            null,
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

    fun removePendingSale(id: String) {
        writableDatabase.delete("pending_sales", "id=?", arrayOf(id))
    }

    fun setPendingError(id: String, error: String?) {
        writableDatabase.update(
            "pending_sales",
            ContentValues().apply { put("last_error", error?.take(500)) },
            "id=?",
            arrayOf(id)
        )
    }
}
