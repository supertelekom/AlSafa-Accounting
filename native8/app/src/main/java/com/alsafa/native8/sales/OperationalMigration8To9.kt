package com.alsafa.native8.sales

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object OperationalMigration8To9:Migration(8,9) {
 override fun migrate(db:SupportSQLiteDatabase) {
  db.execSQL("""CREATE TABLE IF NOT EXISTS sale_profit_reversals (
   id TEXT NOT NULL PRIMARY KEY, sale_id TEXT NOT NULL, payment_id TEXT NOT NULL,
   refund_movement_id TEXT NOT NULL, reversed_base TEXT NOT NULL,
   reversed_profit TEXT NOT NULL, reversed_at INTEGER NOT NULL)""")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_profit_reversals_sale_id ON sale_profit_reversals(sale_id)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_profit_reversals_payment_id ON sale_profit_reversals(payment_id)")
  db.query("PRAGMA integrity_check").use { check(it.moveToFirst() && it.getString(0) == "ok") }
 }
}
