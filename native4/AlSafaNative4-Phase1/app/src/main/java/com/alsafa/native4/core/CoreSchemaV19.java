package com.alsafa.native4.core;
import android.database.sqlite.SQLiteDatabase;
/** Additive repair intake media metadata. */
final class CoreSchemaV19 {private CoreSchemaV19(){}static void create(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS repair_media(media_id TEXT PRIMARY KEY,repair_id TEXT NOT NULL,uri TEXT NOT NULL,source TEXT NOT NULL CHECK(source IN ('CAMERA','GALLERY')),created_at INTEGER NOT NULL)");db.execSQL("CREATE INDEX IF NOT EXISTS idx_repair_media_repair ON repair_media(repair_id,created_at)");}}
