package com.alsafa.native8.sales

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserve all historical payments. Null FX fields mean historical rate is unknown. */
object OperationalMigration6To7 : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sale_payments ADD COLUMN invoice_currency TEXT")
        db.execSQL("ALTER TABLE sale_payments ADD COLUMN fx_rate TEXT")
        db.execSQL("ALTER TABLE sale_payments ADD COLUMN credited_amount TEXT")
        db.query("PRAGMA integrity_check").use {
            check(it.moveToFirst() && it.getString(0) == "ok") { "Payment migration integrity failure" }
        }
    }
}
