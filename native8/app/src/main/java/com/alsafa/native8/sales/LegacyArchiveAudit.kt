package com.alsafa.native8.sales

import com.alsafa.native8.data.migration.LegacyStagingDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Read-only reconciliation of staged legacy sales against the Room archive.
 * Does not create receivables, stock movements, cashbox entries or invoices.
 */
class LegacyArchiveAudit(
    private val staging: LegacyStagingDatabase,
    private val operational: AppDatabase
) {
    data class Finding(val invoiceId: String, val field: String, val expected: String, val actual: String)
    data class Report(
        val sourceSha256: String,
        val stagedCount: Int,
        val archivedCount: Int,
        val sourceProductCount: Int,
        val roomProductCount: Int,
        val duplicateSourceIds: Int,
        val findings: List<Finding>
    ) {
        val matches: Boolean get() = stagedCount == archivedCount &&
            sourceProductCount == roomProductCount && duplicateSourceIds == 0 && findings.isEmpty()
    }

    suspend fun inspect(runId: Long): Report = withContext(Dispatchers.IO) {
        val db = staging.readableDatabase
        val source = db.rawQuery(
            "SELECT source_sha256 FROM import_runs WHERE id=?", arrayOf(runId.toString())
        ).use { cursor ->
            require(cursor.moveToFirst()) { "Unknown staging run" }
            cursor.getString(0)
        }
        val findings = mutableListOf<Finding>()
        val ids = mutableSetOf<String>()
        var duplicateIds = 0
        var salesCount = 0
        var archivedCount = 0
        val saleDao = operational.legacySaleDao()
        db.rawQuery(
            "SELECT json FROM legacy_records WHERE run_id=? AND section='sales' ORDER BY ordinal",
            arrayOf(runId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                salesCount++
                val raw = JSONObject(cursor.getString(0))
                val id = raw.optString("id")
                if (!ids.add(id)) duplicateIds++
                val archived = saleDao.legacyById(id)
                if (archived == null) {
                    findings += Finding(id, "archive", "present", "missing")
                    continue
                }
                archivedCount++
                fun compare(field: String, expected: String, actual: String) {
                    if (expected != actual) findings += Finding(id, field, expected, actual)
                }
                compare("source_sha256", source, archived.source_sha256)
                compare("invoice_number", raw.getLong("invoiceNo").toString(), archived.invoice_number.toString())
                val timestamp = LocalDate.parse(raw.getString("date"))
                    .atTime(LocalTime.parse(raw.optString("time", "00:00")))
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                compare("created_at", timestamp.toString(), archived.created_at.toString())
                compare("customer", raw.optString("customer"), archived.customer)
                compare("phone", raw.optString("phone"), archived.phone)
                compare("currency", raw.optString("currency", "USD"), archived.currency)
                compare("total", raw.getString("total").toBigDecimal().stripTrailingZeros().toPlainString(),
                    archived.total.toBigDecimal().stripTrailingZeros().toPlainString())
                val originalPaid = raw.getString("paid").toBigDecimal()
                val archivePaid = archived.historical_paid.toBigDecimal()
                if (archivePaid < originalPaid) {
                    findings += Finding(id, "historical_paid", "at least ${originalPaid.toPlainString()}", archived.historical_paid)
                }
                // The importer may reconcile additional historical payments; do not assume raw paid is final.
                compare("original_json", raw.toString(), JSONObject(archived.original_json).toString())
                val originalLines = raw.getJSONArray("items")
                val lines = saleDao.legacyLines(id)
                compare("line_count", originalLines.length().toString(), lines.size.toString())
                for (index in 0 until minOf(originalLines.length(), lines.size)) {
                    compare("line[$index].ordinal", index.toString(), lines[index].ordinal.toString())
                    compare("line[$index].json", originalLines.getJSONObject(index).toString(),
                        JSONObject(lines[index].original_json).toString())
                }
                if (archived.due.toBigDecimal() < BigDecimal.ZERO) {
                    findings += Finding(id, "due", "nonnegative", archived.due)
                }
            }
        }
        val productCount = db.rawQuery(
            "SELECT COUNT(*) FROM legacy_records WHERE run_id=? AND section='products'",
            arrayOf(runId.toString())
        ).use { it.moveToFirst(); it.getInt(0) }
        val roomProductCount = operational.operationalInventoryReadDao().importedProducts(source).size
        if (productCount != roomProductCount) {
            findings += Finding(source, "product_count", productCount.toString(), roomProductCount.toString())
        }
        Report(source, salesCount, archivedCount, productCount, roomProductCount, duplicateIds, findings)
    }
}
