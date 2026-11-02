package com.alsafa.native8.data.operational

import androidx.room.withTransaction
import com.alsafa.native8.data.migration.LegacyStagingDatabase
import com.alsafa.native8.sales.AppDatabase
import org.json.JSONObject

/** Promote staged products atomically into Room; original JSON is preserved. */
class RoomProductImportPromoter(
    private val staging: LegacyStagingDatabase,
    private val operational: AppDatabase
) {
    data class Result(val count: Int, val alreadyImported: Boolean, val sourceSha256: String)

    suspend fun promote(runId: Long): Result {
        val stagedDb = staging.readableDatabase
        val source = stagedDb.rawQuery(
            "SELECT source_sha256 FROM import_runs WHERE id=?", arrayOf(runId.toString())
        ).use { cursor ->
            require(cursor.moveToFirst()) { "Unknown import run: $runId" }
            cursor.getString(0)
        }
        val rows = mutableListOf<ProductEntity>()
        val seen = HashSet<String>()
        stagedDb.rawQuery(
            "SELECT ordinal, legacy_id, json FROM legacy_records WHERE run_id=? AND section='products' ORDER BY ordinal",
            arrayOf(runId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val ordinal = cursor.getInt(0)
                val id = cursor.getString(1)
                require(!id.isNullOrBlank() && seen.add(id)) { "Missing or duplicate product ID: $id" }
                val raw = cursor.getString(2)
                val obj = JSONObject(raw)
                rows.add(ProductEntity(
                    source, ordinal, id, obj.optString("name"),
                    obj.opt("stock")?.toString(), obj.opt("salePrice")?.toString(),
                    obj.opt("cost")?.toString(), obj.optString("currency"),
                    obj.optString("barcode"), obj.optString("variantGroupId"),
                    obj.optString("variantColor"), raw
                ))
            }
        }
        require(rows.isNotEmpty()) { "Backup contains no product records" }
        return operational.withTransaction {
            val dao = operational.operationalInventoryWriteDao()
            val previous = dao.sourceImport(source)
            if (previous != null) {
                Result(previous.product_count, true, source)
            } else {
                dao.insertSource(SourceImportEntity(source, runId, System.currentTimeMillis(), rows.size))
                dao.insertProducts(rows)
                check(dao.productCount(source) == rows.size) { "Product import count mismatch" }
                Result(rows.size, false, source)
            }
        }
    }
}
