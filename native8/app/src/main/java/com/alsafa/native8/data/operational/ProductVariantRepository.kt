package com.alsafa.native8.data.operational

import android.content.ContentValues
import org.json.JSONObject
import java.util.UUID

/** User-added colors are separate from immutable imported product snapshots. */
class ProductVariantRepository(private val database: OperationalDatabase) {
    private fun parentExists(source: String, id: String): Boolean {
        if (source == "local") return database.readableDatabase.rawQuery(
            "SELECT 1 FROM local_products WHERE local_id=?", arrayOf(id)
        ).use { it.moveToFirst() }
        return database.readableDatabase.rawQuery(
            "SELECT 1 FROM products WHERE source_sha256=? AND legacy_id=?", arrayOf(source, id)
        ).use { it.moveToFirst() }
    }

    fun list(source: String, parentId: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        database.readableDatabase.rawQuery(
            "SELECT variant_id, variant_json FROM product_variants_local WHERE parent_source=? AND parent_id=? ORDER BY created_at",
            arrayOf(source, parentId)
        ).use { c ->
            while (c.moveToNext()) result.add(JSONObject(c.getString(1)).put("id", c.getString(0)))
        }
        return result
    }

    fun get(id: String): JSONObject? = database.readableDatabase.rawQuery(
        "SELECT variant_json FROM product_variants_local WHERE variant_id=?", arrayOf(id)
    ).use { c -> if (c.moveToFirst()) JSONObject(c.getString(0)).put("id", id) else null }

    fun save(source: String, parentId: String, color: JSONObject, existingId: String? = null): String {
        require(source.isNotBlank() && parentId.isNotBlank())
        val name = color.optString("color").trim()
        require(name.isNotEmpty()) { "اسم اللون مطلوب" }
        for (key in listOf("stock", "salePrice", "cost")) {
            val value = color.optString(key).trim()
            require(value.isBlank() || (value.toBigDecimalOrNull()?.signum() ?: -1) >= 0) { "قيمة غير صالحة: $key" }
        }
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            require(parentExists(source, parentId)) { "المنتج الأصلي غير موجود" }
            if (existingId != null) {
                require(db.rawQuery(
                    "SELECT 1 FROM product_variants_local WHERE variant_id=? AND parent_source=? AND parent_id=?",
                    arrayOf(existingId, source, parentId)
                ).use { it.moveToFirst() }) { "اللون غير موجود لهذا المنتج" }
            }
            val barcode = color.optString("barcode").trim()
            if (barcode.isNotEmpty()) {
                val edits = ProductEditRepository(database)
                edits.checkBarcodeAvailable(barcode, existingId)
            }
            val id = existingId ?: "color-" + UUID.randomUUID()
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put("variant_id", id)
                put("parent_source", source)
                put("parent_id", parentId)
                put("color_name", name)
                put("variant_json", JSONObject(color.toString()).put("id", id).toString())
                put("updated_at", now)
                if (existingId == null) put("created_at", now)
            }
            if (existingId == null) db.insertOrThrow("product_variants_local", null, values)
            else check(db.update("product_variants_local", values, "variant_id=?", arrayOf(id)) == 1)
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }

    fun delete(source: String, parentId: String, id: String): Boolean =
        database.writableDatabase.delete(
            "product_variants_local", "variant_id=? AND parent_source=? AND parent_id=?",
            arrayOf(id, source, parentId)
        ) == 1
}
