package com.alsafa.native4.core;

import android.database.sqlite.SQLiteDatabase;

/** V15: immutable FX snapshots. Original transaction currency remains authoritative; reports may normalize to USD. */
public final class CoreSchemaV15 {
 private CoreSchemaV15(){}
 public static void create(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS fx_rates(currency TEXT PRIMARY KEY,usd_per_unit REAL NOT NULL,updated_at INTEGER NOT NULL,source TEXT NOT NULL DEFAULT 'MANUAL')");
  db.execSQL("INSERT OR IGNORE INTO fx_rates(currency,usd_per_unit,updated_at,source) VALUES('USD',1.0,0,'SYSTEM')");
  db.execSQL("CREATE TABLE IF NOT EXISTS fx_snapshots(snapshot_id TEXT PRIMARY KEY,source_type TEXT NOT NULL,source_id TEXT NOT NULL,currency TEXT NOT NULL,original_amount REAL NOT NULL,usd_per_unit REAL NOT NULL,usd_amount REAL NOT NULL,captured_at INTEGER NOT NULL)");
  db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_fx_snapshot_source ON fx_snapshots(source_type,source_id)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_fx_snapshot_time ON fx_snapshots(captured_at)");
 }
}
