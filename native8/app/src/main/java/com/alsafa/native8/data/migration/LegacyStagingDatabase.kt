package com.alsafa.native8.data.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Lossless import staging. No financial posting occurs in this database.
 * Operational tables are intentionally NOT defined until legacy parity is approved.
 */
class LegacyStagingDatabase(context: Context) :
    SQLiteOpenHelper(context, "alsafa_native8_staging.db", null, 1) {

    val stagingCacheDir: java.io.File = context.cacheDir

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE import_runs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_sha256 TEXT NOT NULL UNIQUE,
            format_version INTEGER NOT NULL,
            exported_at TEXT,
            app_version TEXT,
            imported_at INTEGER NOT NULL,
            record_count INTEGER NOT NULL DEFAULT 0
        )""")
        db.execSQL("""CREATE TABLE legacy_records (
            run_id INTEGER NOT NULL,
            section TEXT NOT NULL,
            ordinal INTEGER NOT NULL,
            legacy_id TEXT,
            json TEXT NOT NULL,
            PRIMARY KEY (run_id, section, ordinal),
            FOREIGN KEY (run_id) REFERENCES import_runs(id)
        )""")
        db.execSQL("CREATE INDEX idx_legacy_section_id ON legacy_records(run_id, section, legacy_id)")
        db.execSQL("""CREATE TABLE legacy_objects (
            run_id INTEGER NOT NULL,
            section TEXT NOT NULL,
            json TEXT NOT NULL,
            PRIMARY KEY(run_id, section),
            FOREIGN KEY(run_id) REFERENCES import_runs(id)
        )""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Staging schema migration must be explicit; never destroy imported records")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
}
