package com.alsafa.native8.sales

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit non-destructive v5 -> v6 schema preparation.
 *
 * Activation is deliberately separate from this definition: the Room v6 schema
 * must include all fifteen entities, and legacy call sites must be migrated
 * before AppDatabase can safely use this migration.
 */
object OperationalMigration5To6 : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Reject partial or unexpected operational files instead of silently
        // creating empty financial tables over missing history.
        val required = arrayOf(
            "source_imports", "products", "product_edits", "local_products",
            "product_variants_local", "product_stock_movements", "sales",
            "sale_lines", "sale_payments", "sale_returns",
            "cashbox_movements", "customer_receivables"
        )
        for (table in required) {
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use {
                check(it.moveToFirst()) { "Missing operational table: $table" }
            }
        }
        // SQLite legacy TEXT PRIMARY KEY does not imply NOT NULL on ordinary rowid tables.
        // Room requires non-null primary keys. Rebuild only when needed and preserve all rows.
        db.query("PRAGMA table_info(sales)").use { columns ->
            val nameIndex = columns.getColumnIndexOrThrow("name")
            val notNullIndex = columns.getColumnIndexOrThrow("notnull")
            var rebuild = false
            while (columns.moveToNext()) {
                if (columns.getString(nameIndex) == "id" && columns.getInt(notNullIndex) == 0) {
                    rebuild = true
                }
            }
            if (rebuild) {
                db.query("SELECT COUNT(*) FROM sales WHERE id IS NULL").use { invalid ->
                    check(invalid.moveToFirst() && invalid.getLong(0) == 0L) {
                        "Cannot migrate sales: null invoice IDs found"
                    }
                }
                db.execSQL("CREATE TABLE sales_room_migration (id TEXT NOT NULL PRIMARY KEY, number INTEGER NOT NULL UNIQUE, created_at INTEGER NOT NULL, customer TEXT NOT NULL, phone TEXT NOT NULL, total TEXT NOT NULL, discount TEXT NOT NULL, paid TEXT NOT NULL, status TEXT NOT NULL)")
                db.execSQL("INSERT INTO sales_room_migration (id, number, created_at, customer, phone, total, discount, paid, status) SELECT id, number, created_at, customer, phone, total, discount, paid, status FROM sales")
                db.execSQL("DROP TABLE sales")
                db.execSQL("ALTER TABLE sales_room_migration RENAME TO sales")
            }
        }
        // Room requires explicit NOT NULL on SQLite TEXT primary keys.
        db.query("PRAGMA table_info(sale_lines)").use { info ->
            var rebuild = false
            while (info.moveToNext()) {
                if (info.getString(1) == "id" && info.getInt(3) == 0) rebuild = true
            }
            if (rebuild) {
                db.query("SELECT COUNT(*) FROM sale_lines WHERE id IS NULL").use { rows ->
                    check(rows.moveToFirst() && rows.getLong(0) == 0L) { "Null sale line IDs" }
                }
                db.execSQL("CREATE TABLE sale_lines_room_migration (id TEXT NOT NULL PRIMARY KEY, sale_id TEXT NOT NULL, source TEXT NOT NULL, product_id TEXT NOT NULL, variant_id TEXT, title TEXT NOT NULL, quantity TEXT NOT NULL, unit_price TEXT NOT NULL, unit_cost TEXT NOT NULL, discount TEXT NOT NULL, returned_qty TEXT NOT NULL DEFAULT '0')")
                db.execSQL("INSERT INTO sale_lines_room_migration SELECT id, sale_id, source, product_id, variant_id, title, quantity, unit_price, unit_cost, discount, returned_qty FROM sale_lines")
                db.execSQL("DROP TABLE sale_lines")
                db.execSQL("ALTER TABLE sale_lines_room_migration RENAME TO sale_lines")
            }
        }
        // Upgrade remaining legacy TEXT PRIMARY KEY columns to explicit NOT NULL.
        // Each table is copied in the same Room migration transaction.
        val remaining = listOf(
            Triple("sale_payments", "id", "id TEXT NOT NULL PRIMARY KEY, sale_id TEXT NOT NULL, cashbox TEXT NOT NULL, currency TEXT NOT NULL, amount TEXT NOT NULL, created_at INTEGER NOT NULL"),
            Triple("sale_returns", "id", "id TEXT NOT NULL PRIMARY KEY, sale_id TEXT NOT NULL, line_id TEXT NOT NULL, quantity TEXT NOT NULL, created_at INTEGER NOT NULL, reason TEXT NOT NULL"),
            Triple("cashbox_movements", "id", "id TEXT NOT NULL PRIMARY KEY, cashbox TEXT NOT NULL, currency TEXT NOT NULL, amount TEXT NOT NULL, sale_id TEXT NOT NULL, created_at INTEGER NOT NULL, note TEXT NOT NULL"),
            Triple("customer_receivables", "sale_id", "sale_id TEXT NOT NULL PRIMARY KEY, customer TEXT NOT NULL, phone TEXT NOT NULL, balance TEXT NOT NULL")
        )
        for ((table, key, definition) in remaining) {
            var rebuild = false
            val columns = mutableListOf<String>()
            db.query("PRAGMA table_info($table)").use { info ->
                while (info.moveToNext()) {
                    val column = info.getString(1)
                    columns.add(column)
                    if (column == key && info.getInt(3) == 0) rebuild = true
                }
            }
            if (rebuild) {
                db.query("SELECT COUNT(*) FROM $table WHERE $key IS NULL").use { invalid ->
                    check(invalid.moveToFirst() && invalid.getLong(0) == 0L) {
                        "Null primary key in $table; refusing migration"
                    }
                }
                val expected = when (table) {
                    "sale_payments" -> listOf("id", "sale_id", "cashbox", "currency", "amount", "created_at")
                    "sale_returns" -> listOf("id", "sale_id", "line_id", "quantity", "created_at", "reason")
                    "cashbox_movements" -> listOf("id", "cashbox", "currency", "amount", "sale_id", "created_at", "note")
                    else -> listOf("sale_id", "customer", "phone", "balance")
                }
                check(columns.toSet() == expected.toSet()) {
                    "Unexpected columns in $table; refusing migration"
                }
                val temp = table + "_room_migration"
                val fields = expected.joinToString(", ")
                db.execSQL("CREATE TABLE $temp ($definition)")
                db.execSQL("INSERT INTO $temp ($fields) SELECT $fields FROM $table")
                db.execSQL("DROP TABLE $table")
                db.execSQL("ALTER TABLE $temp RENAME TO $table")
            }
        }
        // Existing UNIQUE constraints remain in place. Explicit Room indexes
        // add schema metadata without rebuilding or deleting any ledger rows.
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_sales_number_unique ON sales(number)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_variants_parent_color_unique ON product_variants_local(parent_source, parent_id, color_name COLLATE NOCASE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sales_created ON sales(created_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_legacy_id ON products(source_sha256, legacy_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_group ON products(source_sha256, variant_group_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_product_stock_movements_parent ON product_stock_movements(parent_source, parent_id, created_at)")
        db.execSQL("CREATE TABLE IF NOT EXISTS legacy_sales_archive (legacy_id TEXT PRIMARY KEY NOT NULL, source_sha256 TEXT NOT NULL, invoice_number INTEGER NOT NULL, created_at INTEGER NOT NULL, customer TEXT NOT NULL, phone TEXT NOT NULL, currency TEXT NOT NULL, total TEXT NOT NULL, historical_paid TEXT NOT NULL, due TEXT NOT NULL, reconciliation TEXT NOT NULL, status TEXT NOT NULL, original_json TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_legacy_sales_number ON legacy_sales_archive(invoice_number)")
        db.execSQL("CREATE TABLE IF NOT EXISTS legacy_sales_lines (id TEXT PRIMARY KEY NOT NULL, sale_id TEXT NOT NULL, ordinal INTEGER NOT NULL, original_json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS legacy_sales_imports (source_sha256 TEXT PRIMARY KEY NOT NULL, imported_at INTEGER NOT NULL, invoice_count INTEGER NOT NULL, review_count INTEGER NOT NULL)")
        db.query("PRAGMA foreign_key_check").use {
            check(!it.moveToFirst()) { "Foreign-key violations; migration aborted" }
        }
        db.query("PRAGMA integrity_check").use {
            check(it.moveToFirst() && it.getString(0) == "ok") { "SQLite integrity check failed" }
        }
    }
}
