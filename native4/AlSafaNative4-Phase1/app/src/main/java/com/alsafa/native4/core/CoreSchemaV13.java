package com.alsafa.native4.core;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** V13: immutable capital snapshot on each sold line. Additive only. */
public final class CoreSchemaV13 {
 private CoreSchemaV13(){}
 public static void create(SQLiteDatabase db){
  add(db,"invoice_items","unit_cost_snapshot","REAL NOT NULL DEFAULT 0");
  add(db,"invoice_items","cost_total_snapshot","REAL NOT NULL DEFAULT 0");
  add(db,"invoice_items","profit_snapshot","REAL NOT NULL DEFAULT 0");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_invoice_items_profit_invoice ON invoice_items(invoice_id)");
 }
 private static void add(SQLiteDatabase db,String table,String column,String definition){try(Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null)){while(c.moveToNext())if(column.equals(c.getString(c.getColumnIndexOrThrow("name"))))return;}db.execSQL("ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);}
}
