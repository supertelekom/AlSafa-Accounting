package com.alsafa.native8.data.operational

import androidx.room.withTransaction
import com.alsafa.native8.sales.AppDatabase
import org.json.JSONObject

/** Room-only color repository; no SQLiteOpenHelper access. */
class RoomProductVariantRepository(private val db: AppDatabase) {
    private val dao get() = db.operationalInventoryWriteDao()
    private val inventory = RoomInventoryRepository(db)
    private val edits = RoomProductEditRepository(db)

    suspend fun list(source: String, parentId: String): List<JSONObject> =
        dao.variants(source, parentId).map { JSONObject(it.variant_json).put("id", it.variant_id) }

    suspend fun get(id: String): JSONObject? =
        dao.variant(id)?.let { JSONObject(it.variant_json).put("id", it.variant_id) }

    suspend fun save(source: String, parentId: String, color: JSONObject, existingId: String? = null): String =
        db.withTransaction {
            val merged = existingId?.let { get(it) ?: error("اللون غير موجود") } ?: JSONObject()
            for (key in color.keys()) merged.put(key, color.get(key))
            val barcode = merged.optString("barcode").trim()
            edits.checkBarcodeAvailable(barcode, excludeVariantId = existingId)
            inventory.saveVariant(source, parentId, merged, existingId)
        }

    suspend fun delete(source: String, parentId: String, id: String): Boolean =
        inventory.deleteVariant(source, parentId, id)
}
