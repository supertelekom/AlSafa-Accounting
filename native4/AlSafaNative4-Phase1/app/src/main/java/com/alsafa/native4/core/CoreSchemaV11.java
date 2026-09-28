package com.alsafa.native4.core;

import android.database.sqlite.SQLiteDatabase;

/** Additive schema for the remaining AlSafa 3.9.308 feature backbone. Never drops business data. */
public final class CoreSchemaV11 {
 private CoreSchemaV11(){}
 public static void create(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS product_images(image_id TEXT PRIMARY KEY,product_id TEXT NOT NULL,local_uri TEXT,remote_url TEXT,sort_order INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL,deleted_at INTEGER)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_product_images_product ON product_images(product_id,sort_order)");
  db.execSQL("CREATE TABLE IF NOT EXISTS product_barcodes(barcode TEXT PRIMARY KEY,product_id TEXT NOT NULL,kind TEXT NOT NULL DEFAULT 'SELLER',created_at INTEGER NOT NULL,deleted_at INTEGER)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_product_barcodes_product ON product_barcodes(product_id)");
  db.execSQL("CREATE TABLE IF NOT EXISTS customers(customer_id TEXT PRIMARY KEY,name TEXT NOT NULL,phone TEXT,points REAL NOT NULL DEFAULT 0,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,deleted_at INTEGER)");
  db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_phone ON customers(phone) WHERE phone IS NOT NULL AND phone<>''");
  db.execSQL("CREATE TABLE IF NOT EXISTS loyalty_moves(move_id TEXT PRIMARY KEY,customer_id TEXT NOT NULL,source_type TEXT NOT NULL,source_id TEXT,points REAL NOT NULL,created_at INTEGER NOT NULL)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_loyalty_customer ON loyalty_moves(customer_id,created_at)");
  db.execSQL("CREATE TABLE IF NOT EXISTS repair_inventory(part_id TEXT PRIMARY KEY,name TEXT NOT NULL,barcode TEXT,quantity INTEGER NOT NULL DEFAULT 0 CHECK(quantity>=0),unit_cost REAL NOT NULL DEFAULT 0 CHECK(unit_cost>=0),sale_price REAL NOT NULL DEFAULT 0 CHECK(sale_price>=0),currency TEXT NOT NULL DEFAULT 'USD',created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,deleted_at INTEGER)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_repair_inventory_name ON repair_inventory(name)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_repair_inventory_barcode ON repair_inventory(barcode)");
  db.execSQL("CREATE TABLE IF NOT EXISTS repair_inventory_moves(move_id TEXT PRIMARY KEY,part_id TEXT NOT NULL,source_type TEXT NOT NULL,source_id TEXT,before_qty INTEGER NOT NULL,delta_qty INTEGER NOT NULL,after_qty INTEGER NOT NULL,created_at INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS quick_repairs(quick_id TEXT PRIMARY KEY,name TEXT NOT NULL,part_id TEXT,default_price REAL NOT NULL DEFAULT 0,currency TEXT NOT NULL DEFAULT 'USD',sort_order INTEGER NOT NULL DEFAULT 0,active INTEGER NOT NULL DEFAULT 1,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS reservations(reservation_id TEXT PRIMARY KEY,customer_name TEXT NOT NULL,customer_phone TEXT,product_id TEXT,notes TEXT,status TEXT NOT NULL DEFAULT 'OPEN',deposit REAL NOT NULL DEFAULT 0,currency TEXT NOT NULL DEFAULT 'USD',created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,deleted_at INTEGER)");
  db.execSQL("CREATE TABLE IF NOT EXISTS partners(partner_id TEXT PRIMARY KEY,name TEXT NOT NULL,profit_percent REAL NOT NULL DEFAULT 0,active INTEGER NOT NULL DEFAULT 1,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS partner_products(link_id TEXT PRIMARY KEY,partner_id TEXT NOT NULL,product_id TEXT NOT NULL,capital REAL NOT NULL DEFAULT 0,currency TEXT NOT NULL DEFAULT 'USD',created_at INTEGER NOT NULL,deleted_at INTEGER)");
  db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_partner_product_unique ON partner_products(partner_id,product_id) WHERE deleted_at IS NULL");
  db.execSQL("CREATE TABLE IF NOT EXISTS withdrawals(withdrawal_id TEXT PRIMARY KEY,person TEXT NOT NULL,amount REAL NOT NULL CHECK(amount>=0),currency TEXT NOT NULL,payment_source TEXT NOT NULL,notes TEXT,created_at INTEGER NOT NULL,deleted_at INTEGER)");
  db.execSQL("CREATE TABLE IF NOT EXISTS cash_transfers(transfer_id TEXT PRIMARY KEY,from_account TEXT NOT NULL,to_account TEXT NOT NULL,amount REAL NOT NULL CHECK(amount>=0),currency TEXT NOT NULL,notes TEXT,created_at INTEGER NOT NULL,deleted_at INTEGER)");
  db.execSQL("CREATE TABLE IF NOT EXISTS after_sales(case_id TEXT PRIMARY KEY,source_type TEXT NOT NULL,source_id TEXT NOT NULL,customer_id TEXT,status TEXT NOT NULL DEFAULT 'OPEN',notes TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,closed_at INTEGER)");
  db.execSQL("CREATE TABLE IF NOT EXISTS app_settings(setting_key TEXT PRIMARY KEY,setting_value TEXT NOT NULL,updated_at INTEGER NOT NULL)");
 }
}
