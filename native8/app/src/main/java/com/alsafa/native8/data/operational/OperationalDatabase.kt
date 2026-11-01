package com.alsafa.native8.data.operational

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Native 8 operational store. Imported product snapshots are not financial postings. */
class OperationalDatabase(context: Context) :
    SQLiteOpenHelper(context, "alsafa_native8_operational.db", null, 2) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE source_imports (
            source_sha256 TEXT PRIMARY KEY,
            source_run_id INTEGER NOT NULL,
            imported_at INTEGER NOT NULL,
            product_count INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE products (
            source_sha256 TEXT NOT NULL,
            source_ordinal INTEGER NOT NULL,
            legacy_id TEXT NOT NULL,
            name TEXT,
            stock_text TEXT,
            sale_price_text TEXT,
            cost_text TEXT,
            currency TEXT,
            barcode TEXT,
            variant_group_id TEXT,
            variant_color TEXT,
            original_json TEXT NOT NULL,
            PRIMARY KEY(source_sha256, source_ordinal),
            FOREIGN KEY(source_sha256) REFERENCES source_imports(source_sha256)
        )""")
        db.execSQL("CREATE INDEX idx_products_legacy_id ON products(source_sha256, legacy_id)")
        db.execSQL("CREATE INDEX idx_products_group ON products(source_sha256, variant_group_id)")
        createEditTables(db)
    }

    private fun createEditTables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE product_edits (
            source_sha256 TEXT NOT NULL,
            legacy_id TEXT NOT NULL,
            edited_json TEXT NOT NULL,
            base_sha256 TEXT NOT NULL,
            updated_at INTEGER NOT NULL,
            PRIMARY KEY(source_sha256, legacy_id),
            FOREIGN KEY(source_sha256) REFERENCES source_imports(source_sha256)
        )""")
        db.execSQL("""CREATE TABLE local_products (
            local_id TEXT PRIMARY KEY,
            product_json TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2) {
            createEditTables(db)
            return
        }
        error("Unsupported operational migration: $oldVersion -> $newVersion")
    }
}
