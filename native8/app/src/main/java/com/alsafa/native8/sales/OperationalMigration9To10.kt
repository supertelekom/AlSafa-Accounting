package com.alsafa.native8.sales

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive only: preserve all sales, stock and cashbox rows. */
object OperationalMigration9To10:Migration(9,10) {
 override fun migrate(db:SupportSQLiteDatabase) {
  db.execSQL("""CREATE TABLE IF NOT EXISTS supplier_archive (
   archive_key TEXT NOT NULL PRIMARY KEY,source_sha256 TEXT NOT NULL,legacy_id TEXT NOT NULL,
   supplier_id TEXT NOT NULL,name TEXT NOT NULL,original_json TEXT NOT NULL,imported_at INTEGER NOT NULL)""")
  db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_supplier_archive_source_sha256_legacy_id ON supplier_archive(source_sha256,legacy_id)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_archive_supplier_id ON supplier_archive(supplier_id)")
  db.execSQL("""CREATE TABLE IF NOT EXISTS purchase_archive (
   archive_key TEXT NOT NULL PRIMARY KEY,source_sha256 TEXT NOT NULL,legacy_id TEXT NOT NULL,
   supplier_id TEXT NOT NULL,currency TEXT NOT NULL,invoice_number TEXT NOT NULL,
   invoice_date TEXT NOT NULL,total_exact TEXT NOT NULL,paid_at_import_exact TEXT NOT NULL,
   original_json TEXT NOT NULL,imported_at INTEGER NOT NULL)""")
  db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_archive_source_sha256_legacy_id ON purchase_archive(source_sha256,legacy_id)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_archive_supplier_id_currency ON purchase_archive(supplier_id,currency)")
  db.execSQL("""CREATE TABLE IF NOT EXISTS supplier_ledger_archive (
   archive_key TEXT NOT NULL PRIMARY KEY,source_sha256 TEXT NOT NULL,legacy_id TEXT NOT NULL,
   supplier_id TEXT NOT NULL,currency TEXT NOT NULL,kind TEXT NOT NULL,
   amount_exact TEXT NOT NULL,reference_id TEXT,original_json TEXT NOT NULL,imported_at INTEGER NOT NULL)""")
  db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_supplier_ledger_archive_source_sha256_legacy_id ON supplier_ledger_archive(source_sha256,legacy_id)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_ledger_archive_supplier_id_currency ON supplier_ledger_archive(supplier_id,currency)")
  db.execSQL("""CREATE TABLE IF NOT EXISTS supplier_payments (
   payment_id TEXT NOT NULL PRIMARY KEY,purchase_archive_key TEXT,supplier_id TEXT NOT NULL,
   currency TEXT NOT NULL,amount_exact TEXT NOT NULL,vault TEXT NOT NULL,
   paid_at INTEGER NOT NULL,cash_movement_id TEXT NOT NULL)""")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payments_purchase_archive_key ON supplier_payments(purchase_archive_key)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payments_supplier_id_currency ON supplier_payments(supplier_id,currency)")
  db.query("PRAGMA integrity_check").use { check(it.moveToFirst() && it.getString(0)=="ok") }
 }
}
