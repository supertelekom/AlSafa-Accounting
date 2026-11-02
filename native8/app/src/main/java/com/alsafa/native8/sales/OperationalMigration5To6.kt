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
