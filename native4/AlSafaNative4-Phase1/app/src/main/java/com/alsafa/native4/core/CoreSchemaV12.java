package com.alsafa.native4.core;

import android.database.sqlite.SQLiteDatabase;

/** Additive schema for online orders: shipping responsibility and partial/later collections. */
public final class CoreSchemaV12 {
 private CoreSchemaV12(){}
 public static void create(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS online_sale_settlements(settlement_id TEXT PRIMARY KEY,sale_id TEXT NOT NULL,merchandise_total REAL NOT NULL,currency TEXT NOT NULL,shipping_amount REAL NOT NULL DEFAULT 0,shipping_currency TEXT NOT NULL,shipping_payer TEXT NOT NULL,total_due REAL NOT NULL,paid_total REAL NOT NULL DEFAULT 0,remaining REAL NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_online_settlement_sale ON online_sale_settlements(sale_id)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_online_settlement_remaining ON online_sale_settlements(remaining)");
  db.execSQL("CREATE TABLE IF NOT EXISTS online_sale_payments(payment_id TEXT PRIMARY KEY,settlement_id TEXT NOT NULL,amount REAL NOT NULL,currency TEXT NOT NULL,payment_method TEXT NOT NULL,payment_type TEXT NOT NULL,created_at INTEGER NOT NULL)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_online_payment_settlement ON online_sale_payments(settlement_id,created_at)");
 }
}
