package com.alsafa.native4.core;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Additive sale invoice metadata for 3.9.353 workflow parity. Never recreates invoice data. */
public final class CoreSchemaV25 {
 private CoreSchemaV25(){}
 public static void create(SQLiteDatabase db){
  add(db,"invoices","sale_channel","TEXT NOT NULL DEFAULT 'STORE'");
  add(db,"invoices","payment_status","TEXT NOT NULL DEFAULT 'PAID'");
  add(db,"invoices","tax_amount","REAL NOT NULL DEFAULT 0");
  add(db,"invoices","warranty_text","TEXT");
  add(db,"invoices","notes","TEXT");
  add(db,"invoices","business_at","INTEGER");
 }
 private static void add(SQLiteDatabase db,String table,String column,String definition){try(Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null)){while(c.moveToNext())if(column.equals(c.getString(c.getColumnIndexOrThrow("name"))))return;}db.execSQL("ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);}
}
