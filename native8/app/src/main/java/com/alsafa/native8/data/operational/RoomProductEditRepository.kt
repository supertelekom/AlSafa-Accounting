package com.alsafa.native8.data.operational

import androidx.room.withTransaction
import com.alsafa.native8.sales.AppDatabase
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Room-only edits and local product writes; originals are never overwritten. */
class RoomProductEditRepository(private val database: AppDatabase) {
    private val dao get() = database.operationalInventoryWriteDao()
    private val inventory = RoomInventoryRepository(database)

    private fun hash(raw: String): String = MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    /** Check all three barcode namespaces within the caller's transaction. */
    suspend fun checkBarcodeAvailable(
        barcode: String, excludeSource: String? = null,
        excludeProductId: String? = null, excludeVariantId: String? = null
    ) {
        if (barcode.isBlank()) return
        val edits = dao.allEdits().associateBy { it.source_sha256 to it.legacy_id }
        for (variant in dao.allVariants()) {
            if (variant.variant_id != excludeVariantId &&
                JSONObject(variant.variant_json).optString("barcode") == barcode)
                error("الباركود مستخدم للون آخر")
        }
        for (local in dao.localProducts()) {
            if (!(excludeSource == "local" && local.local_id == excludeProductId) &&
                JSONObject(local.product_json).optString("barcode") == barcode)
                error("الباركود مستخدم لمنتج محلي آخر")
        }
        for (product in dao.allImported()) {
            if (product.source_sha256 == excludeSource && product.legacy_id == excludeProductId) continue
            val original = JSONObject(product.original_json)
            val patch = edits[product.source_sha256 to product.legacy_id]?.let { JSONObject(it.edited_json) }
            val value = patch?.optString("barcode", original.optString("barcode"))
                ?: original.optString("barcode")
            if (value == barcode) error("الباركود مستخدم لمنتج مستورد آخر")
        }
    }

    suspend fun resolved(source: String, id: String): JSONObject? = inventory.resolved(source, id)

    suspend fun localProducts(): List<JSONObject> =
        dao.localProducts().map { JSONObject(it.product_json) }

    suspend fun localById(id: String): JSONObject? =
        dao.localProduct(id)?.let { JSONObject(it.product_json) }

    private fun validate(product: JSONObject) {
        require(product.optString("name").isNotBlank()) { "Product name required" }
        for (field in listOf("stock", "salePrice", "cost")) {
            val value = product.optString(field).trim()
            require(value.isEmpty() || (value.toBigDecimalOrNull()?.signum() ?: -1) >= 0) {
                "Invalid numeric field: $field"
            }
        }
    }

    suspend fun createLocal(product: JSONObject): String = database.withTransaction {
        validate(product)
        checkBarcodeAvailable(product.optString("barcode").trim())
        val id = "local-" + UUID.randomUUID()
        val now = System.currentTimeMillis()
        dao.insertLocal(LocalProductEntity(
            id, JSONObject(product.toString()).put("id", id).toString(), now, now
        ))
        id
    }

    suspend fun updateLocal(id: String, product: JSONObject) = database.withTransaction {
        require(id.startsWith("local-"))
        val previous = dao.localProduct(id) ?: error("Local product not found")
        val merged = JSONObject(previous.product_json)
        for (key in product.keys()) merged.put(key, product.get(key))
        merged.put("id", id)
        validate(merged)
        checkBarcodeAvailable(merged.optString("barcode").trim(), "local", id)
        check(dao.updateLocal(previous.copy(
            product_json = merged.toString(), updated_at = System.currentTimeMillis()
        )) == 1)
    }

    suspend fun saveEdit(source: String, id: String, changed: JSONObject) = database.withTransaction {
        require(source.isNotBlank() && id.isNotBlank() && source != "local")
        val rows = dao.byOriginalId(source, id)
        require(rows.size == 1) { "Unknown or duplicate original product" }
        val base = rows.single().original_json
        val digest = hash(base)
        val old = dao.edit(source, id)
        if (old != null) check(old.base_sha256 == digest) { "Product source changed; edit needs review" }
        if (changed.has("barcode")) {
            checkBarcodeAvailable(changed.optString("barcode").trim(), source, id)
        }
        val patch = old?.let { JSONObject(it.edited_json) } ?: JSONObject()
        val allowed = setOf("name","brand","model","description","barcode","category",
            "salePrice","cost","currency","photo","photo2","photo3","barcodePhoto")
        for (key in allowed) {
            if (changed.has(key)) {
                val value = changed.opt(key)
                require(value is String) { "Expected text field: $key" }
                if (key == "salePrice" || key == "cost") {
                    require(value.isBlank() || (value.toBigDecimalOrNull()?.signum() ?: -1) >= 0)
                }
                patch.put(key, value)
            }
        }
        require(patch.length() > 0) { "No editable fields supplied" }
        val entity = ProductEditEntity(source, id, patch.toString(), digest, System.currentTimeMillis())
        if (old == null) dao.insertEdit(entity)
        else check(dao.updateEdit(entity) == 1)
    }
}
