package com.alsafa.native8.sales

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Read-only checks against the actual Room database. Never deletes or repairs rows.
 * Use on an isolated database copy before accepting a schema migration.
 */
object RoomFinancialIntegrityAudit {
    data class Result(
        val orphanLines: Long,
        val orphanPayments: Long,
        val orphanReturns: Long,
        val orphanReturnLines: Long,
        val orphanCashboxMovements: Long,
        val orphanReceivables: Long,
        val duplicateInvoiceNumbers: Long,
        val legacyNewNumberCollisions: Long,
        val foreignKeyViolations: Long
    ) {
        val clean: Boolean get() = listOf(
            orphanLines, orphanPayments, orphanReturns, orphanReturnLines,
            orphanCashboxMovements, orphanReceivables, duplicateInvoiceNumbers,
            legacyNewNumberCollisions, foreignKeyViolations
        ).all { it == 0L }
    }

    fun inspect(db: SupportSQLiteDatabase): Result {
        fun count(sql: String): Long = db.query(sql).use { cursor ->
            check(cursor.moveToFirst()) { "Count query returned no rows" }
            cursor.getLong(0)
        }
        val fkViolations = db.query("PRAGMA foreign_key_check").use { cursor ->
            var total = 0L
            while (cursor.moveToNext()) total++
            total
        }
        // Ledger references are polymorphic: maintenance, transfers and purchases are not sales orphans.
        return Result(
            orphanLines = count("SELECT COUNT(*) FROM sale_lines l LEFT JOIN sales s ON s.id=l.sale_id WHERE s.id IS NULL"),
            orphanPayments = count("SELECT COUNT(*) FROM sale_payments p LEFT JOIN sales s ON s.id=p.sale_id WHERE s.id IS NULL"),
            orphanReturns = count("SELECT COUNT(*) FROM sale_returns r LEFT JOIN sales s ON s.id=r.sale_id WHERE s.id IS NULL"),
            orphanReturnLines = count("SELECT COUNT(*) FROM sale_returns r LEFT JOIN sale_lines l ON l.id=r.line_id AND l.sale_id=r.sale_id WHERE l.id IS NULL"),
            orphanCashboxMovements = count("SELECT COUNT(*) FROM cashbox_movements c LEFT JOIN sales s ON s.id=c.sale_id WHERE c.sale_id LIKE 'sale-%' AND s.id IS NULL"),
            orphanReceivables = count("SELECT COUNT(*) FROM customer_receivables d LEFT JOIN sales s ON s.id=d.sale_id WHERE s.id IS NULL"),
            duplicateInvoiceNumbers = count("SELECT COUNT(*) FROM (SELECT number FROM sales GROUP BY number HAVING COUNT(*)>1)"),
            legacyNewNumberCollisions = count("SELECT COUNT(*) FROM sales s INNER JOIN legacy_sales_archive a ON a.invoice_number=s.number"),
            foreignKeyViolations = fkViolations
        )
    }
}
