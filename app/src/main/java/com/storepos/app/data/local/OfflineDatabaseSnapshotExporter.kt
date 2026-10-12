package com.storepos.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Android 8+ SQLite backup without VACUUM INTO. Reads the source in one
 * SQLite transaction (blocking concurrent writes during the short copy),
 * creates an independent database, then checks integrity and exported bytes.
 * Never modifies or deletes the live database.
 */
internal object OfflineDatabaseSnapshotExporter {
    data class Result(val bytes: Long, val sha256: String, val tables: Int)

    fun export(context: Context, destination: Uri): Result {
        val stage = File(context.cacheDir, "storepos-backup-" + System.nanoTime() + ".db")
        try {
            val expected: Map<String, Long>
            val version: Int
            val helper = OfflineStore(context)
            try {
                val original = helper.writableDatabase
                original.beginTransactionNonExclusive()
                try {
                    version = original.version
                    check(version > 0) { "Invalid SQLite schema version." }
                    expected = snapshotToFile(original, stage, version)
                } finally {
                    // Read-only source transaction: no sales or stock changed.
                    original.endTransaction()
                }
            } finally {
                helper.close()
            }
            verify(stage, version, expected)
            val stagedHash = stage.inputStream().use(::sha256)
            val bytes = context.contentResolver.openOutputStream(destination, "w")?.use { out ->
                stage.inputStream().use { it.copyTo(out) }
            } ?: error("Cannot write to the selected backup folder.")
            check(bytes.toLong() == stage.length()) {
                "Backup was not completely written; please choose another folder."
            }
            // Verify the actual SAF document, not just temporary staging data.
            val savedHash = context.contentResolver.openInputStream(destination)?.use(::sha256)
                ?: error("Cannot verify the saved backup. Try another folder.")
            check(savedHash == stagedHash) {
                "Backup verification failed. Do not use that exported file."
            }
            return Result(stage.length(), savedHash, expected.size)
        } finally {
            stage.delete()
            File(stage.path + "-wal").delete()
            File(stage.path + "-shm").delete()
            File(stage.path + "-journal").delete()
        }
    }

    private fun snapshotToFile(
        live: SQLiteDatabase,
        stage: File,
        version: Int
    ): Map<String, Long> {
        check(!stage.exists()) { "A staging database already exists." }
        val dest = SQLiteDatabase.openDatabase(
            stage.absolutePath, null, SQLiteDatabase.CREATE_IF_NECESSARY
        )
        try {
            val tables = schema(live, "table").filterNot { specialTable(it.first) }
            check(tables.isNotEmpty()) { "Offline database has no tables to back up." }
            val counts = linkedMapOf<String, Long>()
            dest.beginTransaction()
            try {
                for ((name, createSql) in tables) {
                    dest.execSQL(createSql)
                    var copied = 0L
                    live.rawQuery("SELECT * FROM " + quoted(name), null).use { cursor ->
                        val columns = Array(cursor.columnCount) { cursor.getColumnName(it) }
                        while (cursor.moveToNext()) {
                            dest.insertOrThrow(name, null, row(cursor, columns))
                            copied++
                        }
                    }
                    counts[name] = copied
                }
                // Create indexes and triggers only after all table contents
                // are copied so a trigger cannot change archived transactions.
                for (kind in listOf("index", "trigger", "view")) {
                    schema(live, kind).forEach { (_, sql) -> dest.execSQL(sql) }
                }
                dest.version = version
                dest.setTransactionSuccessful()
            } finally {
                dest.endTransaction()
            }
            return counts
        } finally {
            dest.close()
        }
    }

    private fun schema(db: SQLiteDatabase, type: String): List<Pair<String, String>> =
        db.rawQuery(
            "SELECT name, sql FROM sqlite_master WHERE type=? AND sql IS NOT NULL ORDER BY name",
            arrayOf(type)
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1))
            }
        }

    private fun specialTable(name: String): Boolean =
        name.startsWith("sqlite_") || name == "android_metadata"

    internal fun quoted(name: String): String =
        "\"" + name.replace("\"", "\"\"") + "\""

    private fun row(cursor: Cursor, columns: Array<String>): ContentValues {
        val values = ContentValues(columns.size)
        for (i in columns.indices) {
            val name = columns[i]
            when (cursor.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> values.putNull(name)
                Cursor.FIELD_TYPE_INTEGER -> values.put(name, cursor.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> values.put(name, cursor.getDouble(i))
                Cursor.FIELD_TYPE_STRING -> values.put(name, cursor.getString(i))
                Cursor.FIELD_TYPE_BLOB -> values.put(name, cursor.getBlob(i))
                else -> error("Unrecognized data type in local SQLite.")
            }
        }
        return values
    }

    private fun verify(
        file: File,
        version: Int,
        expected: Map<String, Long>
    ) {
        check(file.isFile && file.length() > 0) { "Empty backup file." }
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            check(db.version == version) { "Backup schema version mismatch." }
            check(
                schema(db, "table").map { it.first }.filterNot(::specialTable).toSet() ==
                    expected.keys
            ) { "Backup missing tables." }
            for ((table, total) in expected) {
                db.rawQuery("SELECT count(*) FROM " + quoted(table), null).use { cursor ->
                    check(cursor.moveToFirst() && cursor.getLong(0) == total) {
                        "Incomplete backup table: " + table
                    }
                }
            }
            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0).equals("ok", true)) {
                    "SQLite integrity verification failed."
                }
            }
        }
    }

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16_384)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (n > 0) digest.update(buffer, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
