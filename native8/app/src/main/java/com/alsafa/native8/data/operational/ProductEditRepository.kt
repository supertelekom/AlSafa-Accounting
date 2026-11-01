package com.alsafa.native8.data.operational

import android.content.ContentValues
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * Isolated user changes. Never mutates imported source JSON or financial balances.
 * A source change must be reconciled explicitly before applying an old edit.
 */
class ProductEditRepository(private val database: OperationalDatabase) {
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun saveEdit(source: String, id: String, changed: JSONObject) {
        require(source.isNotBlank() && id.isNotBlank())
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val base = db.rawQuery(
                "SELECT original_json FROM products WHERE source_sha256=? AND legacy_id=? LIMIT 2",
                arrayOf(source, id)
            ).use { c ->
                require(c.moveToFirst()) { "Unknown original product" }
                val raw = c.getString(0)
                check(!c.moveToNext()) { "Duplicate original product ID" }
                raw
            }
            // Keep original identity, inventory, funding and accounting fields untouched.
            val allowed = setOf("name", "brand", "model", "description", "barcode", "category")
            val original = JSONObject(base)
            val patch = JSONObject()
            for (key in allowed) {
                if (changed.has(key)) {
                    val value = changed.opt(key)
                    require(value is String) { "Expected text field: $key" }
                    patch.put(key, value)
                }
            }
            require(patch.length() > 0) { "No editable fields supplied" }
            val values = ContentValues().apply {
                put("source_sha256", source)
                put("legacy_id", id)
                put("edited_json", patch.toString())
                put("base_sha256", sha256(base))
                put("updated_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict("product_edits", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun resolved(source: String, id: String): JSONObject? {
        val db = database.readableDatabase
        val base = db.rawQuery(
            "SELECT original_json FROM products WHERE source_sha256=? AND legacy_id=?",
            arrayOf(source, id)
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: return null
        val result = JSONObject(base)
        db.rawQuery(
            "SELECT edited_json, base_sha256 FROM product_edits WHERE source_sha256=? AND legacy_id=?",
            arrayOf(source, id)
        ).use { c ->
            if (c.moveToFirst()) {
                check(c.getString(1) == sha256(base)) { "Product source changed; edit needs review" }
                val patch = JSONObject(c.getString(0))
                for (key in patch.keys()) result.put(key, patch.get(key))
            }
        }
        return result
    }

    fun createLocal(product: JSONObject): String {
        require(product.optString("name").isNotBlank()) { "Product name required" }
        val id = "local-" + UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        database.writableDatabase.insertOrThrow("local_products", null, ContentValues().apply {
            put("local_id", id)
            put("product_json", JSONObject(product.toString()).put("id", id).toString())
            put("created_at", now)
            put("updated_at", now)
        })
        return id
    }
}
