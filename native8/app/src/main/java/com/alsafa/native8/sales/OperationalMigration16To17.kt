package com.alsafa.native8.sales

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Non-destructive: unknown historical channel remains NULL until verified. */
object OperationalMigration16To17:Migration(16,17) {
 override fun migrate(db:SupportSQLiteDatabase) {
  db.execSQL("ALTER TABLE sales ADD COLUMN channel TEXT DEFAULT NULL")
 }
}
