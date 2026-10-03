package com.alsafa.native4.core;

import android.database.sqlite.SQLiteDatabase;

/** V15: work-day FX rates plus immutable transaction snapshots for USD reporting. */
public final class CoreSchemaV15 {
 private CoreSchemaV15(){}
 public static void create(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS fx_rates(currency TEXT PRIMARY KEY,usd_per_unit REAL NOT NULL,updated_at INTEGER NOT NULL,source TEXT NOT NULL DEFAULT 'MANUAL')");
  db.execSQL("INSERT OR IGNORE INTO fx_rates(currency,usd_per_unit,updated_at,source) VALUES('USD',1.0,0,'SYSTEM')");
  db.execSQL("CREATE TABLE IF NOT EXISTS daily_fx_rates(business_day TEXT PRIMARY KEY,try_per_usd REAL NOT NULL,syp_per_usd REAL NOT NULL,syp_per_try REAL NOT NULL,updated_at INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS fx_snapshots(snapshot_id TEXT PRIMARY KEY,source_type TEXT NOT NULL,source_id TEXT NOT NULL,currency TEXT NOT NULL,original_amount REAL NOT NULL,usd_per_unit REAL NOT NULL,usd_amount REAL NOT NULL,captured_at INTEGER NOT NULL)");
  db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_fx_snapshot_source ON fx_snapshots(source_type,source_id)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_fx_snapshot_time ON fx_snapshots(captured_at)");
 }
}
