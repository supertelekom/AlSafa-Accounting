package com.alsafa.native8.data.operational

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.alsafa.native8.data.migration.LegacyStagingDatabase
import org.json.JSONObject

/**
 * Promote a validated, staged product snapshot into the operational database.
 * Does NOT change stock, book profits, or post historical ledger movements.
 * Source IDs and original JSON are retained for later relational migration.
 */
class ProductImportPromoter(
    private val staging: LegacyStagingDatabase,
    private val operational: OperationalDatabase
) {
    data class Result(val count: Int, val alreadyImported: Boolean, val sourceSha256: String)

    fun promote(runId: Long): Result {
        val source = staging.readableDatabase.rawQuery(
            "SELECT source_sha256 FROM import_runs WHERE id=?",
            arrayOf(runId.toString())
        ).use { cursor ->
            require(cursor.moveToFirst()) { "Unknown import run: $runId" }
            cursor.getString(0)
        }
        val db = operational.writableDatabase
        db.beginTransaction()
        try {
            val previous = db.rawQuery(
                "SELECT product_count FROM source_imports WHERE source_sha256=?",
                arrayOf(source)
            ).use { c -> if (c.moveToFirst()) c.getInt(0) else null }
            if (previous != null) {
                db.setTransactionSuccessful()
                return Result(previous, true, source)
            }
            // Count before insertion, and keep the source snapshot in the same transaction.
            val count = staging.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM legacy_records WHERE run_id=? AND section='products'",
                arrayOf(runId.toString())
            ).use { c -> c.moveToFirst(); c.getInt(0) }
            require(count > 0) { "Backup contains no product records" }
            db.insertOrThrow("source_imports", null, ContentValues().apply {
                put("source_sha256", source)
                put("source_run_id", runId)
                put("imported_at", System.currentTimeMillis())
                put("product_count", count)
            })
            val seen = HashSet<String>()
            var inserted = 0
            staging.readableDatabase.rawQuery(
                """SELECT ordinal, legacy_id, json FROM legacy_records
                   WHERE run_id=? AND section='products' ORDER BY ordinal""",
                arrayOf(runId.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val ordinal = cursor.getInt(0)
                    val id = cursor.getString(1)
                    require(!id.isNullOrBlank()) { "Product at ordinal $ordinal has no original ID" }
                    require(seen.add(id)) { "Duplicate product ID: $id" }
                    val raw = cursor.getString(2)
                    val product = JSONObject(raw)
                    db.insertOrThrow("products", null, ContentValues().apply {
                        put("source_sha256", source)
                        put("source_ordinal", ordinal)
                        put("legacy_id", id)
                        put("name", product.optString("name"))
                        put("stock_text", product.opt("stock")?.toString())
                        put("sale_price_text", product.opt("salePrice")?.toString())
                        put("cost_text", product.opt("cost")?.toString())
                        put("currency", product.optString("currency"))
                        put("barcode", product.optString("barcode"))
                        put("variant_group_id", product.optString("variantGroupId"))
                        put("variant_color", product.optString("variantColor"))
                        put("original_json", raw)
                    })
                    inserted++
                }
            }
            check(inserted == count) { "Product import count mismatch" }
            db.setTransactionSuccessful()
            return Result(inserted, false, source)
        } finally {
            db.endTransaction()
        }
    }
}
