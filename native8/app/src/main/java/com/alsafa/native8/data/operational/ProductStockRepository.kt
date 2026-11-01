package com.alsafa.native8.data.operational

import android.content.ContentValues
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

/** Inventory-only movement ledger. Never posts sales, profit, funding or cash. */
class ProductStockRepository(private val database: OperationalDatabase) {
    data class Movement(val delta: String, val before: String, val after: String, val note: String, val time: Long)

    fun history(source: String, parentId: String, variantId: String? = null): List<Movement> {
        val result = mutableListOf<Movement>()
        val selection = if (variantId == null) "variant_id IS NULL" else "variant_id=?"
        val args = if (variantId == null) arrayOf(source, parentId) else arrayOf(source, parentId, variantId)
        database.readableDatabase.rawQuery(
            "SELECT quantity_delta,quantity_before,quantity_after,note,created_at FROM product_stock_movements WHERE parent_source=? AND parent_id=? AND " + selection + " ORDER BY created_at DESC",
            args
        ).use { c ->
            while (c.moveToNext()) result.add(Movement(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4)))
        }
        return result
    }

    fun adjust(source: String, parentId: String, variantId: String?, deltaText: String, note: String): String {
        require(note.isNotBlank()) { "سبب الحركة مطلوب" }
        val delta = deltaText.toBigDecimalOrNull() ?: error("الكمية غير صالحة")
        require(delta.signum() != 0) { "الكمية صفر" }
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val current: JSONObject
            val before: BigDecimal
            val variantRepo = ProductVariantRepository(database)
            val productRepo = ProductEditRepository(database)
            if (variantId != null) {
                val match = db.rawQuery("SELECT 1 FROM product_variants_local WHERE variant_id=? AND parent_source=? AND parent_id=?", arrayOf(variantId, source, parentId)).use { it.moveToFirst() }
                require(match) { "اللون غير مرتبط بهذا المنتج" }
                current = variantRepo.get(variantId) ?: error("اللون غير موجود")
            } else if (source == "local") {
                current = productRepo.localById(parentId) ?: error("المنتج غير موجود")
            } else {
                current = productRepo.resolved(source, parentId) ?: error("المنتج المستورد غير موجود")
            }
            before = current.optString("stock", "0").ifBlank { "0" }.toBigDecimalOrNull() ?: error("الكمية الحالية غير صالحة")
            val after = before.add(delta)
            require(after.signum() >= 0) { "الكمية غير كافية" }
            current.put("stock", after.stripTrailingZeros().toPlainString())
            if (variantId != null) variantRepo.save(source, parentId, current, variantId)
            else if (source == "local") productRepo.updateLocal(parentId, current)
            else error("تسوية المنتج المستورد تتطلب رصيداً تشغيلياً مستقلاً")
            val id = "stock-" + UUID.randomUUID()
            db.insertOrThrow("product_stock_movements", null, ContentValues().apply {
                put("movement_id", id)
                put("parent_source", source)
                put("parent_id", parentId)
                put("variant_id", variantId)
                put("quantity_delta", delta.stripTrailingZeros().toPlainString())
                put("quantity_before", before.stripTrailingZeros().toPlainString())
                put("quantity_after", after.stripTrailingZeros().toPlainString())
                put("note", note.trim())
                put("created_at", System.currentTimeMillis())
            })
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }
}
