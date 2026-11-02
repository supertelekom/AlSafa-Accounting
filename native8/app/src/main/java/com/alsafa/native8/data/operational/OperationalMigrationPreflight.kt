package com.alsafa.native8.data.operational

import android.database.sqlite.SQLiteDatabase

/**
 * Read-only comparison of an operational v5 fixture and its migrated v6 copy.
 * The historical archive tables were optional in the SQLiteOpenHelper v5 baseline.
 */
object OperationalMigrationPreflight {
    private val baseline = listOf(
        "source_imports", "products", "product_edits", "local_products",
        "product_variants_local", "product_stock_movements", "sales",
        "sale_lines", "sale_payments", "sale_returns",
        "cashbox_movements", "customer_receivables"
    )
    private val addedByMigration = listOf(
        "legacy_sales_archive", "legacy_sales_lines", "legacy_sales_imports"
    )
    private val all = baseline + addedByMigration

    data class Snapshot(
        val userVersion: Int,
        val rowCounts: Map<String, Long>,
        val maxInvoiceNumber: Long,
        val receivableBalances: Map<String, String>,
        val foreignKeyViolations: Int,
        val presentTables: Set<String> = rowCounts.keys
    )

    fun capture(db: SQLiteDatabase): Snapshot {
        require(db.version == 5 || db.version == 6) {
            "Expected operational SQLite v5/v6, found ${db.version}"
        }
        val names = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }
        val required = if (db.version == 5) baseline else all
        val missing = required.filterNot { it in names }
        require(missing.isEmpty()) { "Missing operational tables: ${missing.joinToString()}" }
        val present = all.filter { it in names }
        val counts = present.associateWith { table ->
            db.rawQuery("SELECT COUNT(*) FROM \"$table\"", null).use {
                check(it.moveToFirst()); it.getLong(0)
            }
        }
        val maxInvoice = if ("legacy_sales_archive" in names) {
            db.rawQuery(
                "SELECT MAX(n) FROM (SELECT COALESCE(MAX(number),0) AS n FROM sales " +
                    "UNION ALL SELECT COALESCE(MAX(invoice_number),0) AS n FROM legacy_sales_archive)",
                null
            ).use { check(it.moveToFirst()); it.getLong(0) }
        } else {
            db.rawQuery("SELECT COALESCE(MAX(number),0) FROM sales", null).use {
                check(it.moveToFirst()); it.getLong(0)
            }
        }
        val balances = db.rawQuery(
            "SELECT sale_id,balance FROM customer_receivables ORDER BY sale_id", null
        ).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }
        val fk = db.rawQuery("PRAGMA foreign_key_check", null).use { c ->
            var count = 0
            while (c.moveToNext()) count++
            count
        }
        val integrity = db.rawQuery("PRAGMA integrity_check", null).use {
            check(it.moveToFirst()); it.getString(0)
        }
        check(integrity == "ok") { "SQLite integrity_check failed: $integrity" }
        check(fk == 0) { "Foreign key violations: $fk" }
        val colorSql = db.rawQuery(
            "SELECT sql FROM sqlite_master WHERE type='table' AND name='product_variants_local'",
            null
        ).use { check(it.moveToFirst()); it.getString(0) }
        check(colorSql.contains("COLLATE NOCASE", ignoreCase = true)) {
            "Color-name NOCASE collation missing"
        }
        return Snapshot(db.version, counts, maxInvoice, balances, fk, present.toSet())
    }

    fun assertPreserved(before: Snapshot, after: Snapshot) {
        check(before.userVersion == 5 && after.userVersion == 6) {
            "Expected version transition 5 to 6"
        }
        check(after.presentTables.containsAll(all)) { "Missing migrated archive tables" }
        for ((table, count) in before.rowCounts) {
            check(after.rowCounts[table] == count) { "Row count changed: $table" }
        }
        for (table in addedByMigration - before.presentTables) {
            check(after.rowCounts[table] == 0L) {
                "New archive table unexpectedly contains rows: $table"
            }
        }
        check(before.maxInvoiceNumber == after.maxInvoiceNumber) {
            "Invoice numbering changed"
        }
        check(before.receivableBalances == after.receivableBalances) {
            "Receivable balances changed"
        }
        check(after.foreignKeyViolations == 0) { "Foreign keys invalid after migration" }
    }
}
