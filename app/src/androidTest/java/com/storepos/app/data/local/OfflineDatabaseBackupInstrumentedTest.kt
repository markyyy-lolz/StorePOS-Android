package com.storepos.app.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OfflineDatabaseBackupInstrumentedTest {
    @Test
    fun capturesOriginalRowsAndReceiptBlobsWithoutVacuumInto() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val key = UUID.randomUUID().toString()
        val sourceFile = File(context.cacheDir, "sqlite-source-" + key + ".db")
        val targetFile = File(context.cacheDir, "sqlite-backup-" + key + ".db")
        val receipt = byteArrayOf(0x1b, 0x40, 0x10, 0x00, 0x7f)
        try {
            SQLiteDatabase.openDatabase(
                sourceFile.absolutePath, null, SQLiteDatabase.CREATE_IF_NECESSARY
            ).use { db ->
                db.execSQL("CREATE TABLE cache_blob (cache_key TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_at INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE offline_receipts (client_key TEXT PRIMARY KEY, shop_id TEXT NOT NULL, receipt_number TEXT NOT NULL, paper_width INTEGER NOT NULL, thermal_bytes BLOB NOT NULL, created_at INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX idx_receipts_shop ON offline_receipts(shop_id)")
                db.insertOrThrow("cache_blob", null, ContentValues().apply {
                    put("cache_key", "products:test")
                    put("payload", "[{\"name\":\"Sample\"}]")
                    put("updated_at", 111L)
                })
                db.insertOrThrow("offline_receipts", null, ContentValues().apply {
                    put("client_key", "test-sale")
                    put("shop_id", "test-shop")
                    put("receipt_number", "OFF-TEST")
                    put("paper_width", 58)
                    put("thermal_bytes", receipt)
                    put("created_at", 222L)
                })
                db.version = 4
                db.enableWriteAheadLogging()
                // Exercise live-file WAL snapshot while the source is still open.
                db.beginTransactionNonExclusive()
                try {
                    val counts = OfflineDatabaseSnapshotExporter.snapshotToFile(db, targetFile, 4)
                    assertEquals(mapOf("cache_blob" to 1L, "offline_receipts" to 1L), counts)
                    OfflineDatabaseSnapshotExporter.verify(targetFile, 4, counts)
                } finally {
                    db.endTransaction()
                }
            }
            SQLiteDatabase.openDatabase(
                targetFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                db.rawQuery("SELECT thermal_bytes FROM offline_receipts WHERE client_key='test-sale'", null).use {
                    assertTrue(it.moveToFirst())
                    assertArrayEquals(receipt, it.getBlob(0))
                }
                db.rawQuery("SELECT count(*) FROM sqlite_master WHERE type='index' AND name='idx_receipts_shop'", null).use {
                    assertTrue(it.moveToFirst())
                    assertEquals(1, it.getInt(0))
                }
                db.rawQuery("PRAGMA integrity_check", null).use {
                    assertTrue(it.moveToFirst())
                    assertEquals("ok", it.getString(0))
                }
            }
        } finally {
            listOf(sourceFile, targetFile).forEach {
                it.delete()
                File(it.path + "-wal").delete()
                File(it.path + "-shm").delete()
                File(it.path + "-journal").delete()
            }
        }
    }
}
