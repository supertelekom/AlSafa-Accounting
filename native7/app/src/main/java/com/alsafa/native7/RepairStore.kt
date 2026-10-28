package com.alsafa.native7

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/** Offline repair storage. All writes are local and transactional. */
class RepairStore(context: Context) : SQLiteOpenHelper(context, "alsafa_native7.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE repairs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            payload TEXT NOT NULL,
            photo_path TEXT,
            status TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            delivered_at INTEGER
        )""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("Database migration must be explicit; refusing destructive upgrade")
    }
    fun save(payload: JSONObject, photoPath: String?, status: String, id: Long? = null): Long {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val values = android.content.ContentValues().apply {
                put("payload", payload.toString())
                put("photo_path", photoPath)
                put("status", status)
                put("updated_at", now)
                if (id == null) put("created_at", now)
            }
            val result = if (id == null) db.insertOrThrow("repairs", null, values)
            else {
                require(db.update("repairs", values, "id=?", arrayOf(id.toString())) == 1) { "Repair not found" }
                id
            }
            db.setTransactionSuccessful()
            return result
        } finally { db.endTransaction() }
    }
    fun list(): JSONArray {
        val result = JSONArray()
        readableDatabase.rawQuery("SELECT id,payload,photo_path,status,created_at,updated_at,delivered_at FROM repairs ORDER BY id DESC", null).use { cursor ->
            while (cursor.moveToNext()) result.put(JSONObject().apply {
                put("id", cursor.getLong(0))
                put("payload", JSONObject(cursor.getString(1)))
                put("photo_path", cursor.getString(2))
                put("status", cursor.getString(3))
                put("created_at", cursor.getLong(4))
                put("updated_at", cursor.getLong(5))
                if (!cursor.isNull(6)) put("delivered_at", cursor.getLong(6))
            })
        }
        return result
    }
    fun deliver(id: Long) {
        val db = writableDatabase
        val values = android.content.ContentValues().apply {
            put("status", "تم التسليم")
            put("delivered_at", System.currentTimeMillis())
            put("updated_at", System.currentTimeMillis())
        }
        require(db.update("repairs", values, "id=? AND delivered_at IS NULL", arrayOf(id.toString())) == 1) {
            "Repair not found or already delivered"
        }
    }
}
