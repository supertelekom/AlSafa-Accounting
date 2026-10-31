package com.alsafa.native7

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Local-only legacy backup storage. Raw JSON is retained losslessly so that
 * future Kotlin repositories can migrate each legacy section without dropping
 * unknown fields. No cloud, JavaScript, or WebView is involved.
 */
internal class LegacyBackupStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "alsafa_native7.db", null, 4) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE legacy_sections (name TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL, item_count INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE legacy_metadata (name TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)")
        createRecordTable(db)
        LegacyOperationalSchema.create(db)
        LegacyDomainTables.create(db)
    }
    private fun createRecordTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS legacy_records (section TEXT NOT NULL, ordinal INTEGER NOT NULL, original_id TEXT, payload TEXT NOT NULL, PRIMARY KEY(section,ordinal))")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_legacy_records_id ON legacy_records(section,original_id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createRecordTable(db)
        if (oldVersion < 3) LegacyOperationalSchema.create(db)
        if (oldVersion < 4) LegacyDomainTables.create(db)
    }

    data class Inspection(val counts: Map<String, Int>, val format: String,
        val sha256: String, val exportedAt: String)
    private fun digest(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    fun inspect(text: String): Inspection {
        val wrapper = JSONObject(text.trimStart('\uFEFF'))
        val wrapped = wrapper.optString("format") == "alsafa-accounting-backup"
        if (wrapped && wrapper.optInt("formatVersion", 0) !in 1..2)
            throw IllegalArgumentException("إصدار النسخة غير مدعوم")
        val data = if (wrapped) wrapper.optJSONObject("data") else wrapper
        require(data != null && data.optJSONObject("settings") != null &&
            data.optJSONArray("sales") != null && data.optJSONArray("products") != null) {
            "الملف لا يحتوي على بيانات الصفا الأساسية"
        }
        val counts = linkedMapOf<String, Int>()
        val keys = data.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = data.opt(key)
            if (value is JSONArray) counts[key] = value.length()
        }
        return Inspection(counts, if (wrapped) "alsafa-accounting-backup" else "legacy-raw",
            digest(text), wrapper.optString("exportedAt"))
    }

    fun hasData(): Boolean = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM legacy_sections", null
    ).use { it.moveToFirst() && it.getInt(0) > 0 }

    /**
     * First import is all-or-nothing. Never silently replace an existing
     * dataset; merging stock and financial histories requires separate rules.
     */
    fun importInitial(text: String, onProgress: (String, Int, Int) -> Unit = { _, _, _ -> }): Inspection {
        val inspection = inspect(text)
        val wrapper = JSONObject(text.trimStart('\uFEFF'))
        val data = if (wrapper.optString("format") == "alsafa-accounting-backup")
            wrapper.getJSONObject("data") else wrapper
        val db = writableDatabase
        db.beginTransaction()
        try {
            check(!hasData()) { "توجد بيانات مستعادة مسبقاً؛ لن نستبدلها أو ندمجها دون فحص" }
            val keys = data.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = data.get(key)
                val raw = value.toString()
                val count = if (value is JSONArray) value.length() else 0
                onProgress(key, 0, count)
                db.execSQL("INSERT INTO legacy_sections(name,payload,item_count) VALUES(?,?,?)",
                    arrayOf<Any?>(key, raw, count))
                if (value is JSONArray) {
                    LegacyOperationalSchema.importSection(db,key,value)
                    LegacyDomainTables.importSection(db,key,value)
                    for (index in 0 until value.length()) {
                        val record = value.get(index)
                        val originalId = (record as? JSONObject)?.optString("id")?.takeIf { it.isNotBlank() }
                        db.execSQL("INSERT INTO legacy_records(section,ordinal,original_id,payload) VALUES(?,?,?,?)",
                            arrayOf<Any?>(key, index, originalId, record.toString()))
                        if ((index + 1) % 100 == 0 || index + 1 == count)
                            onProgress(key, index + 1, count)
                    }
                }
                if (count == 0) onProgress(key, 0, 0)
            }
            db.execSQL("INSERT INTO legacy_metadata(name,payload) VALUES(?,?)",
                arrayOf("original_backup", text))
            db.execSQL("INSERT INTO legacy_metadata(name,payload) VALUES(?,?)",
                arrayOf("backup_sha256",inspection.sha256))
            db.execSQL("INSERT INTO legacy_metadata(name,payload) VALUES(?,?)",
                arrayOf("backup_exported_at",inspection.exportedAt))
            val references = LegacySnapshotVerifier.verify(wrapper)
            db.execSQL("INSERT INTO legacy_metadata(name,payload) VALUES(?,?)",
                arrayOf("unresolved_reference_count", references.unresolved.size.toString()))
            LegacyDomainTables.audit(db)
            val persisted = db.rawQuery("SELECT COUNT(*) FROM legacy_sections", null).use {
                check(it.moveToFirst())
                it.getInt(0)
            }
            check(persisted == data.length()) { "لم يتم حفظ جميع أقسام النسخة" }
            val staged = db.rawQuery("SELECT COUNT(*) FROM legacy_records", null).use {
                check(it.moveToFirst())
                it.getInt(0)
            }
            check(staged == inspection.counts.values.sum()) { "لم يتم حفظ جميع السجلات" }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return inspection
    }

    fun storedCounts(): Map<String, Int> {
        val result = linkedMapOf<String, Int>()
        readableDatabase.rawQuery(
            "SELECT name,item_count FROM legacy_sections ORDER BY name", null
        ).use { cursor ->
            while (cursor.moveToNext()) result[cursor.getString(0)] = cursor.getInt(1)
        }
        return result
    }

    fun originalBackup(): String? = readableDatabase.rawQuery(
        "SELECT payload FROM legacy_metadata WHERE name=?", arrayOf("original_backup")
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    fun section(name: String): String? = readableDatabase.rawQuery(
        "SELECT payload FROM legacy_sections WHERE name=?", arrayOf(name)
    ).use { if (it.moveToFirst()) it.getString(0) else null }
}
