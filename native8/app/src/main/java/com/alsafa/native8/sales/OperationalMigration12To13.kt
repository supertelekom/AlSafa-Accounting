package com.alsafa.native8.sales

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Add maintenance without touching historical sales or cash ledgers. */
object OperationalMigration12To13:Migration(12,13) {
 override fun migrate(db:SupportSQLiteDatabase) {
  db.execSQL("""CREATE TABLE IF NOT EXISTS maintenance_repairs (
   id TEXT NOT NULL PRIMARY KEY,source_sha256 TEXT NOT NULL,invoice_number INTEGER NOT NULL,
   date TEXT NOT NULL,time TEXT NOT NULL,status TEXT NOT NULL,customer TEXT NOT NULL,
   phone TEXT NOT NULL,watch TEXT NOT NULL,model TEXT NOT NULL,problem TEXT NOT NULL,
   currency TEXT NOT NULL,price TEXT NOT NULL,received TEXT NOT NULL,
   delivered_date TEXT NOT NULL,created_at INTEGER NOT NULL,original_json TEXT NOT NULL)""")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_maintenance_repairs_invoice_number ON maintenance_repairs(invoice_number)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_maintenance_repairs_source_sha256 ON maintenance_repairs(source_sha256)")
  db.execSQL("""CREATE TABLE IF NOT EXISTS maintenance_parts (
   id TEXT NOT NULL PRIMARY KEY,source_sha256 TEXT NOT NULL,name TEXT NOT NULL,
   category TEXT NOT NULL,barcode TEXT NOT NULL,stock TEXT NOT NULL,
   currency TEXT NOT NULL,cost TEXT NOT NULL,sale_price TEXT NOT NULL,
   created_at INTEGER NOT NULL,original_json TEXT NOT NULL)""")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_maintenance_parts_source_sha256 ON maintenance_parts(source_sha256)")
 }
}
