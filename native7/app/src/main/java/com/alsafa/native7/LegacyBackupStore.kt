package com.alsafa.native7

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local-only legacy backup storage. Raw JSON is retained losslessly so that
 * future Kotlin repositories can migrate each legacy section without dropping
 * unknown fields. No cloud, JavaScript, or WebView is involved.
 */
internal class LegacyBackupStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "alsafa_native7.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE legacy_sections (name TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL, item_count INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE legacy_metadata (name TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema changes must use additive migrations; never drop tables.
    }

    data class Inspection(val counts: Map<String, Int>, val format: String)
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
        return Inspection(counts, if (wrapped) "alsafa-accounting-backup" else "legacy-raw")
    }

    fun hasData(): Boolean = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM legacy_sections", null
    ).use { it.moveToFirst() && it.getInt(0) > 0 }

    /**
     * First import is all-or-nothing. Never silently replace an existing
     * dataset; merging stock and financial histories requires separate rules.
     */
    fun importInitial(text: String): Inspection {
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
                db.execSQL("INSERT INTO legacy_sections(name,payload,item_count) VALUES(?,?,?)",
                    arrayOf(key, raw, count))
            }
            db.execSQL("INSERT INTO legacy_metadata(name,payload) VALUES(?,?)",
                arrayOf("original_backup", text))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return inspection
    }

    fun section(name: String): String? = readableDatabase.rawQuery(
        "SELECT payload FROM legacy_sections WHERE name=?", arrayOf(name)
    ).use { if (it.moveToFirst()) it.getString(0) else null }
}
