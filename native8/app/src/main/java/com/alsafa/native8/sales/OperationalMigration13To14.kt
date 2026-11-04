package com.alsafa.native8.sales

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Add daily snapshots without rewriting existing ledger or historical closings. */
object OperationalMigration13To14:Migration(13,14) {
 override fun migrate(db:SupportSQLiteDatabase) {
  db.execSQL("""CREATE TABLE IF NOT EXISTS daily_closings_native8 (
   date TEXT NOT NULL PRIMARY KEY,
   created_at INTEGER NOT NULL,
   drawer_actual_json TEXT NOT NULL,
   drawer_transfer_json TEXT NOT NULL,
   drawer_next_json TEXT NOT NULL,
   drawer_difference_json TEXT NOT NULL,
   sham_actual_json TEXT NOT NULL,
   sham_difference_json TEXT NOT NULL,
   notes TEXT NOT NULL)""")
 }
}
