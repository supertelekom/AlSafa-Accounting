package com.alsafa.native4.core;

import android.database.sqlite.SQLiteDatabase;

/** V14: independent product variants and durable product media metadata. Additive only. */
public final class CoreSchemaV14 {
 private CoreSchemaV14(){}
 public static void create(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS product_variants(variant_id TEXT PRIMARY KEY,product_id TEXT NOT NULL,variant_type TEXT NOT NULL DEFAULT 'ADDITIONAL',name TEXT NOT NULL DEFAULT '',barcode TEXT NOT NULL DEFAULT '',dial_color TEXT NOT NULL DEFAULT '',case_color TEXT NOT NULL DEFAULT '',strap_color TEXT NOT NULL DEFAULT '',sale_price REAL NOT NULL DEFAULT 0,quantity INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,deleted_at INTEGER)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_product_variants_product ON product_variants(product_id,deleted_at)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_product_variants_barcode ON product_variants(barcode,deleted_at)");
  db.execSQL("CREATE TABLE IF NOT EXISTS product_media(media_id TEXT PRIMARY KEY,product_id TEXT NOT NULL,variant_id TEXT NOT NULL DEFAULT '',media_type TEXT NOT NULL DEFAULT 'IMAGE',local_uri TEXT NOT NULL DEFAULT '',remote_url TEXT NOT NULL DEFAULT '',sort_order INTEGER NOT NULL DEFAULT 0,is_primary INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,deleted_at INTEGER)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_product_media_product ON product_media(product_id,variant_id,deleted_at,sort_order)");
 }
}
