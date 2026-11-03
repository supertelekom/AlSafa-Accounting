package com.alsafa.native8.sales

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object OperationalMigration10To11:Migration(10,11) {
 override fun migrate(db:SupportSQLiteDatabase) {
  db.execSQL("""CREATE TABLE IF NOT EXISTS purchase_drafts (
   draft_id TEXT NOT NULL PRIMARY KEY,
   supplier_id TEXT NOT NULL,
   currency TEXT NOT NULL,
   note TEXT NOT NULL,
   created_at INTEGER NOT NULL,
   status TEXT NOT NULL)""")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_drafts_supplier_id ON purchase_drafts(supplier_id)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_drafts_created_at ON purchase_drafts(created_at)")
 }
}
