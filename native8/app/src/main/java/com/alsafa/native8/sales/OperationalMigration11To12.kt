package com.alsafa.native8.sales

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object OperationalMigration11To12:Migration(11,12) {
 override fun migrate(db:SupportSQLiteDatabase) {
  db.execSQL("CREATE TABLE IF NOT EXISTS purchase_draft_lines (line_id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL, product_source TEXT NOT NULL, product_id TEXT NOT NULL, product_name TEXT NOT NULL, quantity_exact TEXT NOT NULL, unit_cost_exact TEXT NOT NULL)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_draft_lines_draft_id ON purchase_draft_lines(draft_id)")
 }
}
