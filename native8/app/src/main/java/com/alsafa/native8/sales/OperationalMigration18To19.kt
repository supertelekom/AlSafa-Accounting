package com.alsafa.native8.sales
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object OperationalMigration18To19:Migration(18,19) {
 override fun migrate(db:SupportSQLiteDatabase) {
  db.execSQL("CREATE TABLE IF NOT EXISTS store_profit_adjustments (id TEXT NOT NULL PRIMARY KEY, sale_id TEXT NOT NULL, created_at INTEGER NOT NULL, profit_delta_usd TEXT NOT NULL, reason TEXT NOT NULL)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_store_profit_adjustments_sale_id ON store_profit_adjustments(sale_id)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_store_profit_adjustments_created_at ON store_profit_adjustments(created_at)")
 }
}
