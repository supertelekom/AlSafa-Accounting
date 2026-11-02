package com.alsafa.native8.sales

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Read-only acceptance checks. Run on an isolated copy before a Room schema upgrade.
 * Does not issue INSERT, UPDATE, DELETE, or PRAGMA mutations.
 */
object FinancialIntegrityAcceptance {
    data class Finding(val code: String, val details: String)
    data class Report(
        val integrity: RoomFinancialIntegrityAudit.Result,
        val maxLegacyNumber: Long,
        val maxNewNumber: Long,
        val expectedNextNumber: Long,
        val historicalInvoiceCount: Long,
        val historicalImportCount: Long,
        val findings: List<Finding>
    ) {
        val passed: Boolean get() = integrity.clean && findings.isEmpty()
    }

    fun inspect(db: SupportSQLiteDatabase): Report {
        fun scalar(sql: String): Long = db.query(sql).use { cursor ->
            check(cursor.moveToFirst()) { "Missing scalar result" }
            cursor.getLong(0)
        }
        val integrity = RoomFinancialIntegrityAudit.inspect(db)
        val legacyMax = scalar("SELECT COALESCE(MAX(invoice_number),0) FROM legacy_sales_archive")
        val newMax = scalar("SELECT COALESCE(MAX(number),0) FROM sales")
        val historicalCount = scalar("SELECT COUNT(*) FROM legacy_sales_archive")
        val imports = scalar("SELECT COUNT(*) FROM legacy_sales_imports")
        val findings = mutableListOf<Finding>()
        val next = maxOf(legacyMax, newMax) + 1L
        if (next <= 0L || next == Long.MIN_VALUE) {
            findings += Finding("NUMBER_OVERFLOW", "Invoice number cannot be advanced safely")
        }
        val repeatedLegacyNumbers = scalar(
            "SELECT COUNT(*) FROM (SELECT invoice_number FROM legacy_sales_archive GROUP BY invoice_number HAVING COUNT(*)>1)"
        )
        if (repeatedLegacyNumbers > 0L) {
            findings += Finding("DUPLICATE_LEGACY_NUMBER", repeatedLegacyNumbers.toString())
        }
        val missingLines = scalar(
            "SELECT COUNT(*) FROM legacy_sales_lines l LEFT JOIN legacy_sales_archive s ON s.legacy_id=l.sale_id WHERE s.legacy_id IS NULL"
        )
        if (missingLines > 0L) {
            findings += Finding("ORPHAN_LEGACY_LINE", missingLines.toString())
        }
        val repeatedLineOrdinals = scalar(
            "SELECT COUNT(*) FROM (SELECT sale_id,ordinal FROM legacy_sales_lines GROUP BY sale_id,ordinal HAVING COUNT(*)>1)"
        )
        if (repeatedLineOrdinals > 0L) {
            findings += Finding("DUPLICATE_LEGACY_LINE_ORDINAL", repeatedLineOrdinals.toString())
        }
        val unregisteredArchiveRows = scalar(
            "SELECT COUNT(*) FROM legacy_sales_archive a LEFT JOIN legacy_sales_imports i ON a.source_sha256=i.source_sha256 WHERE i.source_sha256 IS NULL"
        )
        if (unregisteredArchiveRows > 0L) {
            findings += Finding("UNREGISTERED_ARCHIVE_SOURCE", unregisteredArchiveRows.toString())
        }
        val mismatchedImportCounts = scalar(
            "SELECT COUNT(*) FROM legacy_sales_imports i WHERE i.invoice_count != (SELECT COUNT(*) FROM legacy_sales_archive a WHERE a.source_sha256=i.source_sha256)"
        )
        if (mismatchedImportCounts > 0L) {
            findings += Finding("IMPORT_COUNT_MISMATCH", mismatchedImportCounts.toString())
        }
        return Report(integrity, legacyMax, newMax, next, historicalCount, imports, findings)
    }
}
