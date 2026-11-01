package com.alsafa.native8.sales

import android.database.sqlite.SQLiteDatabase

/** Historical invoices are immutable snapshots; never replay old financial movements. */
object LegacySalesArchiveSchema {
    fun ensure(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS legacy_sales_archive (legacy_id TEXT PRIMARY KEY, source_sha256 TEXT NOT NULL, invoice_number INTEGER NOT NULL, created_at INTEGER NOT NULL, customer TEXT NOT NULL, phone TEXT NOT NULL, currency TEXT NOT NULL, total TEXT NOT NULL, historical_paid TEXT NOT NULL, due TEXT NOT NULL, reconciliation TEXT NOT NULL, status TEXT NOT NULL, original_json TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_legacy_sales_number ON legacy_sales_archive(invoice_number)")
        db.execSQL("CREATE TABLE IF NOT EXISTS legacy_sales_lines (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, ordinal INTEGER NOT NULL, original_json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS legacy_sales_imports (source_sha256 TEXT PRIMARY KEY, imported_at INTEGER NOT NULL, invoice_count INTEGER NOT NULL, review_count INTEGER NOT NULL)")
    }
}
