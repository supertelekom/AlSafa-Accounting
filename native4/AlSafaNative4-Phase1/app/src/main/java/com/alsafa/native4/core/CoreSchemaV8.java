package com.alsafa.native4.core;

import android.database.sqlite.SQLiteDatabase;

/** Native4 production-oriented core schema. Additive only: never drops or replaces local business data. */
public final class CoreSchemaV8 {
 private CoreSchemaV8(){}
 public static void create(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS products(product_id TEXT PRIMARY KEY,name TEXT NOT NULL,ref TEXT,barcode TEXT,brand TEXT,category TEXT,variant_type TEXT NOT NULL DEFAULT 'PRIMARY',parent_product_id TEXT,dial_color TEXT,case_color TEXT,strap_color TEXT,quantity INTEGER NOT NULL DEFAULT 0 CHECK(quantity>=0),sale_price REAL NOT NULL DEFAULT 0 CHECK(sale_price>=0),cost_price REAL NOT NULL DEFAULT 0 CHECK(cost_price>=0),currency TEXT NOT NULL DEFAULT 'USD',supplier_id TEXT,customer_details TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,deleted_at INTEGER)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_name ON products(name)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_ref ON products(ref)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode)");
  db.execSQL("CREATE TABLE IF NOT EXISTS invoices(invoice_id TEXT PRIMARY KEY,invoice_no TEXT NOT NULL UNIQUE,invoice_type TEXT NOT NULL,currency TEXT NOT NULL DEFAULT 'USD',subtotal REAL NOT NULL DEFAULT 0,discount REAL NOT NULL DEFAULT 0,total REAL NOT NULL DEFAULT 0,payment_method TEXT NOT NULL,customer_name TEXT,customer_phone TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,deleted_at INTEGER)");
  db.execSQL("CREATE TABLE IF NOT EXISTS invoice_items(item_id TEXT PRIMARY KEY,invoice_id TEXT NOT NULL,product_id TEXT,description TEXT NOT NULL,quantity INTEGER NOT NULL CHECK(quantity>0),unit_price REAL NOT NULL CHECK(unit_price>=0),total REAL NOT NULL CHECK(total>=0),created_at INTEGER NOT NULL)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_invoice_items_invoice ON invoice_items(invoice_id)");
  db.execSQL("CREATE TABLE IF NOT EXISTS stock_moves(move_id TEXT PRIMARY KEY,product_id TEXT NOT NULL,source_type TEXT NOT NULL,source_id TEXT NOT NULL,before_qty INTEGER NOT NULL,delta_qty INTEGER NOT NULL,after_qty INTEGER NOT NULL,created_at INTEGER NOT NULL)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_stock_moves_product ON stock_moves(product_id,created_at)");
  db.execSQL("CREATE TABLE IF NOT EXISTS cash_moves(move_id TEXT PRIMARY KEY,source_type TEXT NOT NULL,source_id TEXT NOT NULL,direction TEXT NOT NULL,amount REAL NOT NULL CHECK(amount>=0),currency TEXT NOT NULL,payment_method TEXT NOT NULL,drawer TEXT NOT NULL DEFAULT 'MAIN',created_at INTEGER NOT NULL)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_cash_moves_created ON cash_moves(created_at)");
  db.execSQL("CREATE TABLE IF NOT EXISTS repairs(repair_id TEXT PRIMARY KEY,invoice_no TEXT NOT NULL UNIQUE,customer_name TEXT NOT NULL,customer_phone TEXT,watch_description TEXT NOT NULL,status TEXT NOT NULL,total REAL NOT NULL DEFAULT 0 CHECK(total>=0),currency TEXT NOT NULL DEFAULT 'USD',payment_method TEXT NOT NULL,received_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,delivered_at INTEGER,deleted_at INTEGER)");
  db.execSQL("CREATE TABLE IF NOT EXISTS repair_parts(item_id TEXT PRIMARY KEY,repair_id TEXT NOT NULL,part_id TEXT,description TEXT NOT NULL,quantity INTEGER NOT NULL DEFAULT 1 CHECK(quantity>0),unit_cost REAL NOT NULL DEFAULT 0,created_at INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS suppliers(supplier_id TEXT PRIMARY KEY,name TEXT NOT NULL,phone TEXT,notes TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,deleted_at INTEGER)");
  db.execSQL("CREATE TABLE IF NOT EXISTS purchases(purchase_id TEXT PRIMARY KEY,supplier_id TEXT,invoice_ref TEXT,currency TEXT NOT NULL DEFAULT 'USD',total REAL NOT NULL DEFAULT 0,payment_source TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,deleted_at INTEGER)");
  db.execSQL("CREATE TABLE IF NOT EXISTS daily_closings(closing_id TEXT PRIMARY KEY,business_day TEXT NOT NULL UNIQUE,expected_usd REAL NOT NULL DEFAULT 0,counted_usd REAL NOT NULL DEFAULT 0,expected_try REAL NOT NULL DEFAULT 0,counted_try REAL NOT NULL DEFAULT 0,expected_syp REAL NOT NULL DEFAULT 0,counted_syp REAL NOT NULL DEFAULT 0,closed_at INTEGER NOT NULL,notes TEXT)");
  db.execSQL("CREATE TABLE IF NOT EXISTS work_days(business_day TEXT PRIMARY KEY,status TEXT NOT NULL DEFAULT 'OPEN',opened_at INTEGER NOT NULL,opening_usd REAL NOT NULL DEFAULT 0 CHECK(opening_usd>=0),opening_try REAL NOT NULL DEFAULT 0 CHECK(opening_try>=0),opening_syp REAL NOT NULL DEFAULT 0 CHECK(opening_syp>=0),source_closing_id TEXT,closing_id TEXT,closed_at INTEGER)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_days_status ON work_days(status,opened_at)");
 }
}
