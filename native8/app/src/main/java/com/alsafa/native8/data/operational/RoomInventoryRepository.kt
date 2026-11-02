package com.alsafa.native8.data.operational

import androidx.room.withTransaction
import com.alsafa.native8.sales.AppDatabase
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

/**
 * Room-only inventory service. Does not open SQLiteOpenHelper.
 * Imported snapshots are immutable; their live stock is the sum of movements.
 */
class RoomInventoryRepository(private val database: AppDatabase) {
    private val dao get() = database.operationalInventoryWriteDao()

    data class ProductRow(val id: String, val ordinal: Int, val data: JSONObject)

    suspend fun latestSource(): String? = dao.latestSource()

    suspend fun page(source: String, offset: Int, limit: Int): List<ProductRow> {
        require(offset >= 0 && limit in 1..500)
        return dao.productPage(source, limit, offset).map {
            ProductRow(it.legacy_id, it.source_ordinal, resolved(source, it.legacy_id)!!)
        }
    }

    suspend fun resolved(source: String, id: String): JSONObject? {
        if (source == "local") return dao.localProduct(id)?.let { JSONObject(it.product_json) }
        val rows = dao.byOriginalId(source, id)
        check(rows.size <= 1) { "Duplicate original product ID: $id" }
        val original = rows.singleOrNull() ?: return null
        val result = JSONObject(original.original_json)
        val patch = dao.edit(source, id)
        if (patch != null) {
            val hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(original.original_json.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            check(hash == patch.base_sha256) { "Source snapshot changed; edit needs review" }
            val edited = JSONObject(patch.edited_json)
            for (key in edited.keys()) result.put(key, edited.get(key))
        }
        return result
    }

    suspend fun stock(source: String, id: String, variantId: String? = null): BigDecimal {
        val product = when {
            variantId != null -> {
                val variant = dao.variant(variantId) ?: error("Variant not found")
                require(variant.parent_source == source && variant.parent_id == id)
                JSONObject(variant.variant_json)
            }
            else -> resolved(source, id) ?: error("Product not found")
        }
        val base = product.optString("stock", "0").ifBlank { "0" }.toBigDecimal()
        if (variantId != null || source == "local") return base
        return dao.stockHistory(source, id, null).fold(base) { sum, row ->
            sum + row.quantity_delta.toBigDecimal()
        }
    }

    /**
     * Caller may nest this in AppDatabase.withTransaction with sale posting.
     * All updates and the movement row roll back together.
     */
    suspend fun adjust(source: String, id: String, variantId: String?,
                       delta: BigDecimal, note: String): String = database.withTransaction {
        require(delta.signum() != 0 && note.isNotBlank())
        val before = stock(source, id, variantId)
        val after = before + delta
        require(after.signum() >= 0) { "Insufficient stock" }
        val now = System.currentTimeMillis()
        if (variantId != null) {
            val variant = dao.variant(variantId) ?: error("Variant not found")
            require(variant.parent_source == source && variant.parent_id == id)
            val json = JSONObject(variant.variant_json).put("stock", after.stripTrailingZeros().toPlainString())
            check(dao.updateVariant(variant.copy(variant_json = json.toString(), updated_at = now)) == 1)
        } else if (source == "local") {
            val product = dao.localProduct(id) ?: error("Local product not found")
            val json = JSONObject(product.product_json).put("stock", after.stripTrailingZeros().toPlainString())
            check(dao.updateLocal(product.copy(product_json = json.toString(), updated_at = now)) == 1)
        }
        val movementId = "stock-" + UUID.randomUUID()
        dao.insertStockMovement(ProductStockMovementEntity(
            movementId, source, id, variantId,
            delta.stripTrailingZeros().toPlainString(),
            before.stripTrailingZeros().toPlainString(),
            after.stripTrailingZeros().toPlainString(),
            note.trim(), now
        ))
        movementId
    }

    suspend fun saveVariant(source: String, id: String, color: JSONObject,
                            existingId: String? = null): String = database.withTransaction {
        require(resolved(source, id) != null) { "Product not found" }
        val name = color.optString("color").trim()
        require(name.isNotBlank()) { "Color required" }
        val now = System.currentTimeMillis()
        val variantId = existingId ?: "color-" + UUID.randomUUID()
        val original = existingId?.let { dao.variant(it) }
        if (existingId != null) {
            require(original != null && original.parent_source == source && original.parent_id == id)
        }
        val merged = original?.let { JSONObject(it.variant_json) } ?: JSONObject()
        for (key in color.keys()) merged.put(key, color.get(key))
        for (field in listOf("stock", "salePrice", "cost")) {
            val value = merged.optString(field).trim()
            require(value.isEmpty() || (value.toBigDecimalOrNull()?.signum() ?: -1) >= 0)
        }
        merged.put("id", variantId)
        val row = ProductVariantEntity(
            variantId, source, id, name, merged.toString(), original?.created_at ?: now, now
        )
        if (original == null) dao.insertVariant(row) else check(dao.updateVariant(row) == 1)
        variantId
    }

    suspend fun deleteVariant(source: String, id: String, variantId: String): Boolean =
        database.withTransaction {
            require(dao.movementCount(variantId) == 0) { "Variant has stock movements" }
            dao.deleteVariant(variantId, source, id) == 1
        }
}
