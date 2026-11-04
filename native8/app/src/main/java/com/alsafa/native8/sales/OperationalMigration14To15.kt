package com.alsafa.native8.sales

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Add-only migration. Never edits imported transactions, sales or closings. */
object OperationalMigration14To15:Migration(14,15) {
 override fun migrate(db:SupportSQLiteDatabase) {
  db.execSQL("""CREATE TABLE IF NOT EXISTS operational_expenses (
   id TEXT NOT NULL PRIMARY KEY,date TEXT NOT NULL,time TEXT NOT NULL,
   category TEXT NOT NULL,notes TEXT NOT NULL,currency TEXT NOT NULL,
   amount TEXT NOT NULL,payment_method TEXT NOT NULL,vault TEXT NOT NULL,
   usd_per_unit TEXT NOT NULL,created_at INTEGER NOT NULL,
   cash_movement_id TEXT NOT NULL)""")
  db.execSQL("""CREATE TABLE IF NOT EXISTS operational_partner_withdrawals (
   id TEXT NOT NULL PRIMARY KEY,date TEXT NOT NULL,time TEXT NOT NULL,
   partner_id TEXT NOT NULL,currency TEXT NOT NULL,amount TEXT NOT NULL,
   vault TEXT NOT NULL,usd_per_unit TEXT NOT NULL,notes TEXT NOT NULL,
   created_at INTEGER NOT NULL,cash_movement_id TEXT NOT NULL)""")
  db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_operational_expenses_cash_movement_id ON operational_expenses(cash_movement_id)")
  db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_operational_partner_withdrawals_cash_movement_id ON operational_partner_withdrawals(cash_movement_id)")
 }
}
