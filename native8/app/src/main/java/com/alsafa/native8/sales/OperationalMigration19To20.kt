package com.alsafa.native8.sales
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
/** Preserve all rows; older invoices have no invented warranty. */
object OperationalMigration19To20:Migration(19,20) {
 override fun migrate(db:SupportSQLiteDatabase) {
  db.execSQL("ALTER TABLE sales ADD COLUMN metadata_json TEXT NOT NULL DEFAULT '{}'")
 }
}
