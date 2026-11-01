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

    private fun ensureBarcodeUnique(barcode: String, excludeSource: String?, excludeId: String?) {
        if (barcode.isBlank()) return
        val db = database.readableDatabase
        db.rawQuery("SELECT local_id, product_json FROM local_products", null).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(0) != excludeId && JSONObject(cursor.getString(1)).optString("barcode") == barcode)
                    error("الباركود مستخدم لمنتج محلي آخر")
            }
        }
        db.rawQuery("SELECT p.source_sha256, p.legacy_id, p.original_json, e.edited_json FROM products p LEFT JOIN product_edits e ON e.source_sha256=p.source_sha256 AND e.legacy_id=p.legacy_id", null).use { cursor ->
            while (cursor.moveToNext()) {
                val source = cursor.getString(0)
                val id = cursor.getString(1)
                if (source == excludeSource && id == excludeId) continue
                val original = JSONObject(cursor.getString(2))
                val patch = cursor.getString(3)?.let { JSONObject(it) }
                val current = patch?.optString("barcode", original.optString("barcode")) ?: original.optString("barcode")
                if (current == barcode) error("الباركود مستخدم لمنتج مستورد آخر")
            }
        }
    }
    fun saveEdit(source: String, id: String, changed: JSONObject) {
        require(source.isNotBlank() && id.isNotBlank())
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            if (changed.has("barcode")) ensureBarcodeUnique(changed.optString("barcode").trim(), source, id)
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
            val allowed = setOf("name", "brand", "model", "description", "barcode", "category", "salePrice", "cost", "currency", "photo", "photo2", "photo3", "barcodePhoto")
            val patch = db.rawQuery(
                "SELECT edited_json, base_sha256 FROM product_edits WHERE source_sha256=? AND legacy_id=?",
                arrayOf(source, id)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    check(cursor.getString(1) == sha256(base)) { "Product source changed; edit needs review" }
                    JSONObject(cursor.getString(0))
                } else JSONObject()
            }
            for (key in allowed) {
                if (changed.has(key)) {
                    val value = changed.opt(key)
                    require(value is String) { "Expected text field: $key" }
                    if (key == "salePrice" || key == "cost") {
                        require(value.isBlank() || (value.toBigDecimalOrNull()?.signum() ?: -1) >= 0) { "Invalid price or cost" }
                    }
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

    fun localProducts(): List<JSONObject> {
        val rows = mutableListOf<JSONObject>()
        database.readableDatabase.rawQuery(
            "SELECT product_json FROM local_products ORDER BY created_at DESC", null
        ).use { c -> while (c.moveToNext()) rows.add(JSONObject(c.getString(0))) }
        return rows
    }

    fun localById(id: String): JSONObject? = database.readableDatabase.rawQuery(
        "SELECT product_json FROM local_products WHERE local_id=?", arrayOf(id)
    ).use { c -> if (c.moveToFirst()) JSONObject(c.getString(0)) else null }

    private fun validateLocal(product: JSONObject) {
        require(product.optString("name").isNotBlank()) { "Product name required" }
        for (field in listOf("stock", "salePrice", "cost")) {
            val value = product.optString(field).trim()
            require(value.isEmpty() || (value.toBigDecimalOrNull()?.signum() ?: -1) >= 0) {
                "Invalid numeric field: $field"
            }
        }
    }
    fun updateLocal(id: String, product: JSONObject) {
        require(id.startsWith("local-"))
        validateLocal(product)
        ensureBarcodeUnique(product.optString("barcode").trim(), null, id)
        val previous = localById(id) ?: error("Local product not found")
        val updated = JSONObject(previous.toString())
        for (key in product.keys()) updated.put(key, product.get(key))
        updated.put("id", id)
        val rows = database.writableDatabase.update(
            "local_products",
            ContentValues().apply {
                put("product_json", updated.toString())
                put("updated_at", System.currentTimeMillis())
            },
            "local_id=?", arrayOf(id)
        )
        check(rows == 1) { "Local product not found" }
    }

    fun createLocal(product: JSONObject): String {
        validateLocal(product)
        ensureBarcodeUnique(product.optString("barcode").trim(), null, null)
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
