package com.alsafa.native8.sales

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Reproduces the historical OperationalDatabase SQLiteOpenHelper v4 -> v5 upgrade.
 * Adds the six sales tables without deleting or rewriting any existing product data.
 */
object OperationalMigration4To5 : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val requiredV4 = arrayOf(
            "source_imports", "products", "product_edits", "local_products",
            "product_variants_local", "product_stock_movements"
        )
        for (table in requiredV4) {
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use {
                check(it.moveToFirst()) { "Missing v4 operational table: $table" }
            }
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS sales (id TEXT PRIMARY KEY, number INTEGER UNIQUE NOT NULL, created_at INTEGER NOT NULL, customer TEXT NOT NULL, phone TEXT NOT NULL, total TEXT NOT NULL, discount TEXT NOT NULL, paid TEXT NOT NULL, status TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS sale_lines (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, source TEXT NOT NULL, product_id TEXT NOT NULL, variant_id TEXT, title TEXT NOT NULL, quantity TEXT NOT NULL, unit_price TEXT NOT NULL, unit_cost TEXT NOT NULL, discount TEXT NOT NULL, returned_qty TEXT NOT NULL DEFAULT '0')")
        db.execSQL("CREATE TABLE IF NOT EXISTS sale_payments (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, cashbox TEXT NOT NULL, currency TEXT NOT NULL, amount TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS sale_returns (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, line_id TEXT NOT NULL, quantity TEXT NOT NULL, created_at INTEGER NOT NULL, reason TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS cashbox_movements (id TEXT PRIMARY KEY, cashbox TEXT NOT NULL, currency TEXT NOT NULL, amount TEXT NOT NULL, sale_id TEXT NOT NULL, created_at INTEGER NOT NULL, note TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS customer_receivables (sale_id TEXT PRIMARY KEY, customer TEXT NOT NULL, phone TEXT NOT NULL, balance TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sales_created ON sales(created_at)")
        db.query("PRAGMA foreign_key_check").use {
            check(!it.moveToFirst()) { "Foreign-key violations during v4 to v5 migration" }
        }
    }
}
