package com.alsafa.native8.sales

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Add audit ledgers without modifying historical invoices or guessing exchange rates. */
object OperationalMigration7To8: Migration(7,8) {
 override fun migrate(db:SupportSQLiteDatabase) {
  db.execSQL("""CREATE TABLE IF NOT EXISTS sale_profit_events (
   payment_id TEXT NOT NULL PRIMARY KEY, sale_id TEXT NOT NULL,
   collected_at INTEGER NOT NULL, credited_base TEXT NOT NULL,
   invoice_net TEXT NOT NULL, cost_basis TEXT NOT NULL, recognized_profit TEXT NOT NULL)""")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_profit_events_sale_id ON sale_profit_events(sale_id)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_profit_events_collected_at ON sale_profit_events(collected_at)")
  db.execSQL("""CREATE TABLE IF NOT EXISTS sale_refund_allocations (
   id TEXT NOT NULL PRIMARY KEY, sale_id TEXT NOT NULL, payment_id TEXT NOT NULL,
   movement_id TEXT NOT NULL, cashbox TEXT NOT NULL, currency TEXT NOT NULL,
   refunded_amount TEXT NOT NULL, credited_base TEXT NOT NULL,
   exchange_rate_to_base TEXT NOT NULL, refunded_at INTEGER NOT NULL)""")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_refund_allocations_sale_id ON sale_refund_allocations(sale_id)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_refund_allocations_payment_id ON sale_refund_allocations(payment_id)")
  db.query("PRAGMA integrity_check").use {
   check(it.moveToFirst() && it.getString(0) == "ok")
  }
 }
}
