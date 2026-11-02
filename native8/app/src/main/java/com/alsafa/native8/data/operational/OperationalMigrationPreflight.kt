package com.alsafa.native8.data.operational

import android.database.sqlite.SQLiteDatabase

/**
 * Read-only release gate for adopting a v5 operational file into Room.
 * Call against a copy of the database, before any schema upgrade.
 * This class never modifies a table, user_version, or room_master_table.
 */
object OperationalMigrationPreflight {
    private val required = listOf(
        "source_imports", "products", "product_edits", "local_products",
        "product_variants_local", "product_stock_movements", "sales",
        "sale_lines", "sale_payments", "sale_returns", "cashbox_movements",
        "customer_receivables", "legacy_sales_archive",
        "legacy_sales_lines", "legacy_sales_imports"
    )

    data class Snapshot(
        val userVersion: Int,
        val rowCounts: Map<String, Long>,
        val maxInvoiceNumber: Long,
        val receivableBalances: Map<String, String>,
        val foreignKeyViolations: Int
    )

    fun capture(db: SQLiteDatabase): Snapshot {
        require(db.version == 5) { "Expected operational SQLite v5, found ${db.version}" }
        val names = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table'", null
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        val missing = required.filterNot { it in names }
        require(missing.isEmpty()) { "Missing operational tables: ${missing.joinToString()}" }
        val counts = required.associateWith { table ->
            db.rawQuery("SELECT COUNT(*) FROM \"$table\"", null).use {
                check(it.moveToFirst())
                it.getLong(0)
            }
        }
        val maxInvoice = db.rawQuery(
            "SELECT MAX(n) FROM (" +
                "SELECT COALESCE(MAX(number),0) AS n FROM sales UNION ALL " +
                "SELECT COALESCE(MAX(invoice_number),0) AS n FROM legacy_sales_archive)",
            null
        ).use { check(it.moveToFirst()); it.getLong(0) }
        val balances = db.rawQuery(
            "SELECT sale_id,balance FROM customer_receivables ORDER BY sale_id", null
        ).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
        }
        val fkViolations = db.rawQuery("PRAGMA foreign_key_check", null).use {
            var count = 0
            while (it.moveToNext()) count++
            count
        }
        val integrity = db.rawQuery("PRAGMA integrity_check", null).use {
            check(it.moveToFirst())
            it.getString(0)
        }
        check(integrity == "ok") { "SQLite integrity_check failed: $integrity" }
        check(fkViolations == 0) { "Foreign key violations: $fkViolations" }
        val colorSql = db.rawQuery(
            "SELECT sql FROM sqlite_master WHERE type='table' AND name='product_variants_local'",
            null
        ).use { check(it.moveToFirst()); it.getString(0) }
        check(colorSql.contains("COLLATE NOCASE", ignoreCase = true)) {
            "Color-name NOCASE collation missing"
        }
        return Snapshot(db.version, counts, maxInvoice, balances, fkViolations)
    }

    /** Compare the same database before and after a migration, using verified copies. */
    fun assertPreserved(before: Snapshot, after: Snapshot) {
        check(before.rowCounts == after.rowCounts) { "Operational row counts changed" }
        check(before.maxInvoiceNumber == after.maxInvoiceNumber) { "Invoice numbering changed" }
        check(before.receivableBalances == after.receivableBalances) { "Receivable balances changed" }
        check(after.foreignKeyViolations == 0) { "Foreign keys invalid after migration" }
    }
}
