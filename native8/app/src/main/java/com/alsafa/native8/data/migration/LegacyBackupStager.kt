package com.alsafa.native8.data.migration

import android.content.ContentValues
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringWriter
import java.security.MessageDigest

/**
 * Phase-1 lossless JSON staging, NOT operational accounting restore.
 * No sales, inventory or vault movements are replayed.
 * Supports formatVersion=2 wrapper and legacy bare data.
 */
class LegacyBackupStager(private val database: LegacyStagingDatabase) {
    data class Result(val sourceSha256: String, val records: Int, val alreadyImported: Boolean)

    fun stage(input: InputStream): Result {
        // Caller supplies a repeatable source (e.g. copied SAF file). A temporary
        // local copy is required to hash and parse the SAME bytes.
        val file = java.io.File.createTempFile("alsafa-v8-", ".json", database.stagingCacheDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            input.use { source ->
                file.outputStream().buffered().use { dest ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        digest.update(buffer, 0, count)
                        dest.write(buffer, 0, count)
                    }
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            val db = database.writableDatabase
            db.rawQuery("SELECT id FROM import_runs WHERE source_sha256=?", arrayOf(sha)).use {
                if (it.moveToFirst()) return Result(sha, 0, true)
            }
            db.beginTransaction()
            try {
                val runValues = ContentValues().apply {
                    put("source_sha256", sha)
                    put("format_version", 2)
                    put("imported_at", System.currentTimeMillis())
                }
                val runId = db.insertOrThrow("import_runs", null, runValues)
                var count = 0
                var version: Int? = null
                var format: String? = null
                var exportedAt: String? = null
                var appVersion: String? = null
                var foundData = false
                var sawSettings = false
                var sawProducts = false
                var sawSales = false
                file.inputStream().buffered().use { stream ->
                    JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (val name = reader.nextName()) {
                                "format" -> format = reader.nextString()
                                "formatVersion" -> version = reader.nextInt()
                                "exportedAt" -> exportedAt = readRaw(reader).trim('"')
                                "appVersion" -> appVersion = readRaw(reader).trim('"')
                                "data" -> {
                                    foundData = true
                                    val stats = stageData(reader, db, runId)
                                    count += stats.first
                                    sawSettings = stats.second.contains("settings")
                                    sawProducts = stats.second.contains("products")
                                    sawSales = stats.second.contains("sales")
                                }
                                else -> {
                                    // Bare data: section arrays are directly at the root.
                                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                                        count += stageArray(reader, db, runId, name)
                                        if (name == "products") sawProducts = true
                                        if (name == "sales") sawSales = true
                                    } else {
                                        saveObject(db, runId, "root." + name, readRaw(reader))
                                        if (name == "settings") sawSettings = true
                                    }
                                }
                            }
                        }
                        reader.endObject()
                        check(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing JSON content" }
                    }
                }
                require(version == null || version == 2) { "Unsupported backup version: $version" }
                require(format == null || format == "alsafa-accounting-backup") {
                    "Unsupported backup format: $format"
                }
                require(foundData || version == null) { "Missing backup data object" }
                require(sawSettings && sawProducts && sawSales) { "Missing essential legacy sections: settings/products/sales" }
                db.execSQL(
                    "UPDATE import_runs SET format_version=?, exported_at=?, app_version=?, record_count=? WHERE id=?",
                    arrayOf(version ?: 2, exportedAt, appVersion, count, runId)
                )
                db.setTransactionSuccessful()
                return Result(sha, count, false)
            } finally {
                db.endTransaction()
            }
        } finally {
            file.delete()
        }
    }

    private fun stageData(reader: JsonReader, db: android.database.sqlite.SQLiteDatabase, runId: Long): Pair<Int, Set<String>> {
        val sections = mutableSetOf<String>()
        var count = 0
        reader.beginObject()
        while (reader.hasNext()) {
            val section = reader.nextName()
            sections.add(section)
            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                count += stageArray(reader, db, runId, section)
            } else {
                saveObject(db, runId, "data." + section, readRaw(reader))
            }
        }
        reader.endObject()
        return count to sections
    }

    private fun stageArray(reader: JsonReader, db: android.database.sqlite.SQLiteDatabase, runId: Long, section: String): Int {
        var ordinal = 0
        reader.beginArray()
        while (reader.hasNext()) {
            val raw = readRaw(reader)
            val legacyId = try { org.json.JSONObject(raw).optString("id").takeIf { it.isNotEmpty() } } catch (_: Exception) { null }
            val values = ContentValues().apply {
                put("run_id", runId)
                put("section", section)
                put("ordinal", ordinal)
                put("legacy_id", legacyId)
                put("json", raw)
            }
            db.insertOrThrow("legacy_records", null, values)
            ordinal++
        }
        reader.endArray()
        return ordinal
    }

    private fun saveObject(db: android.database.sqlite.SQLiteDatabase, runId: Long, section: String, raw: String) {
        val values = ContentValues().apply {
            put("run_id", runId)
            put("section", section)
            put("json", raw)
        }
        db.insertOrThrow("legacy_objects", null, values)
    }

    private fun readRaw(reader: JsonReader): String {
        val output = StringWriter()
        JsonWriter(output).use { writer ->
            // Legacy backup fields may be standalone strings, numbers, booleans or null.
            // Android JsonWriter otherwise rejects primitive roots with:
            // "JSON must start with an array or an object".
            writer.isLenient = true
            fun copyValue() {
                when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> {
                        reader.beginObject(); writer.beginObject()
                        while (reader.hasNext()) { writer.name(reader.nextName()); copyValue() }
                        reader.endObject(); writer.endObject()
                    }
                    JsonToken.BEGIN_ARRAY -> {
                        reader.beginArray(); writer.beginArray()
                        while (reader.hasNext()) copyValue()
                        reader.endArray(); writer.endArray()
                    }
                    JsonToken.STRING -> writer.value(reader.nextString())
                    JsonToken.NUMBER -> {
                        // Preserve the JSON number token as closely as Android's reader permits.
                        val value = reader.nextString()
                        writer.value(object : Number() {
                            override fun toByte() = value.toDouble().toInt().toByte()
                            override fun toChar() = value.toDouble().toInt().toChar()
                            override fun toDouble() = value.toDouble()
                            override fun toFloat() = value.toFloat()
                            override fun toInt() = value.toDouble().toInt()
                            override fun toLong() = value.toDouble().toLong()
                            override fun toShort() = value.toDouble().toInt().toShort()
                            override fun toString() = value
                        })
                    }
                    JsonToken.BOOLEAN -> writer.value(reader.nextBoolean())
                    JsonToken.NULL -> { reader.nextNull(); writer.nullValue() }
                    else -> error("Unexpected JSON token")
                }
            }
            copyValue()
        }
        return output.toString()
    }
}
