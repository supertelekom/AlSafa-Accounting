package com.alsafa.native4.core;

import android.database.sqlite.SQLiteDatabase;

/** V18: reservations for parts selected for an open repair. Reservation is not a sale and creates no cash/profit. */
public final class CoreSchemaV18 {
 private CoreSchemaV18(){}
 public static void create(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS repair_part_reservations(id TEXT PRIMARY KEY,repair_id TEXT NOT NULL,part_id TEXT NOT NULL,quantity INTEGER NOT NULL CHECK(quantity>0),state TEXT NOT NULL DEFAULT 'RESERVED',created_at INTEGER NOT NULL,consumed_at INTEGER,released_at INTEGER,UNIQUE(repair_id,part_id))");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_repair_part_reservations_repair ON repair_part_reservations(repair_id,state)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_repair_part_reservations_part ON repair_part_reservations(part_id,state)");
 }
}
