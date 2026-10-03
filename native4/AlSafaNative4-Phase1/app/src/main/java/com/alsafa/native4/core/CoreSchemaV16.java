package com.alsafa.native4.core;

import android.database.sqlite.SQLiteDatabase;

/** V16: multi-currency payments and two-sided currency exchange ledger. */
public final class CoreSchemaV16 {
 private CoreSchemaV16(){}
 public static void create(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS payment_splits(payment_id TEXT PRIMARY KEY,source_type TEXT NOT NULL,source_id TEXT NOT NULL,amount REAL NOT NULL,currency TEXT NOT NULL,payment_method TEXT NOT NULL,account TEXT NOT NULL,created_at INTEGER NOT NULL)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_payment_splits_source ON payment_splits(source_type,source_id,created_at)");
  db.execSQL("CREATE TABLE IF NOT EXISTS currency_exchanges(exchange_id TEXT PRIMARY KEY,from_account TEXT NOT NULL,from_amount REAL NOT NULL,from_currency TEXT NOT NULL,to_account TEXT NOT NULL,to_amount REAL NOT NULL,to_currency TEXT NOT NULL,business_day TEXT NOT NULL,notes TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_currency_exchanges_day ON currency_exchanges(business_day,created_at)");
 }
}
