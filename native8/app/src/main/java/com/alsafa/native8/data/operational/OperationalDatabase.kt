package com.alsafa.native8.data.operational

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Native 8 operational store. Imported product snapshots are not financial postings. */
class OperationalDatabase(context: Context) :
    SQLiteOpenHelper(context, "alsafa_native8_operational.db", null, 4) {
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
        createVariantTable(db)
        createStockMovementTable(db)
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

    private fun createVariantTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS product_variants_local (variant_id TEXT PRIMARY KEY, parent_source TEXT NOT NULL, parent_id TEXT NOT NULL, color_name TEXT NOT NULL COLLATE NOCASE, variant_json TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, UNIQUE(parent_source, parent_id, color_name))")
    }

    private fun createStockMovementTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS product_stock_movements (movement_id TEXT PRIMARY KEY, parent_source TEXT NOT NULL, parent_id TEXT NOT NULL, variant_id TEXT, quantity_delta TEXT NOT NULL, quantity_before TEXT NOT NULL, quantity_after TEXT NOT NULL, note TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_product_stock_movements_parent ON product_stock_movements(parent_source, parent_id, created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createEditTables(db)
        if (oldVersion < 3) createVariantTable(db)
        if (oldVersion < 4) createStockMovementTable(db)
    }
}
