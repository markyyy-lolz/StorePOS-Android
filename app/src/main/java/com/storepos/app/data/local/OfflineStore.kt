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
    1
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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
