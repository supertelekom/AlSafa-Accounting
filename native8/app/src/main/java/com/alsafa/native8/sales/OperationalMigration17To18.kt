package com.alsafa.native8.sales

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Append-only store invoice-date profit events; no destructive changes. */
object OperationalMigration17To18:Migration(17,18) {
 override fun migrate(db:SupportSQLiteDatabase) {
  db.execSQL("CREATE TABLE IF NOT EXISTS store_sale_profit_events (sale_id TEXT NOT NULL PRIMARY KEY, recognized_at INTEGER NOT NULL, net_revenue_usd TEXT NOT NULL, cost_usd TEXT NOT NULL, profit_usd TEXT NOT NULL)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_store_sale_profit_events_recognized_at ON store_sale_profit_events(recognized_at)")
 }
}
