package com.alsafa.native6.accounting

import android.database.sqlite.SQLiteDatabase
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Independent SQLite partner register. No mutation of historical posted journal rows.
 * Attach to StoreDb after its versioned schema migration has been reviewed.
 */
object PartnerSchema {
    val statements = listOf(
        "CREATE TABLE IF NOT EXISTS partner_members(id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL CHECK(length(trim(name))>0), active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)))",
        "CREATE TABLE IF NOT EXISTS partner_share_periods(id TEXT PRIMARY KEY NOT NULL, effective_date TEXT NOT NULL, note TEXT NOT NULL DEFAULT '')",
        "CREATE TABLE IF NOT EXISTS partner_share_lines(period_id TEXT NOT NULL REFERENCES partner_share_periods(id), partner_id TEXT NOT NULL REFERENCES partner_members(id), percent TEXT NOT NULL, PRIMARY KEY(period_id,partner_id))",
        "CREATE TABLE IF NOT EXISTS partner_drawings(id TEXT PRIMARY KEY NOT NULL, partner_id TEXT NOT NULL REFERENCES partner_members(id), event_id TEXT NOT NULL UNIQUE REFERENCES events(id), actual_date TEXT NOT NULL, currency TEXT NOT NULL CHECK(currency IN ('USD','TRY','SYP')), amount_minor INTEGER NOT NULL CHECK(amount_minor>0))",
        "CREATE INDEX IF NOT EXISTS partner_drawings_month ON partner_drawings(actual_date,currency,partner_id)"
    )
}

/** Read-only monthly report adapter; never reallocates a drawing to an older month. */
class PartnerReportReader(private val db: SQLiteDatabase) {
    fun sharesAt(date: LocalDate): List<PartnerShare> {
        val effective = db.rawQuery(
            "SELECT id FROM partner_share_periods WHERE effective_date<=? ORDER BY effective_date DESC,id DESC LIMIT 1",
            arrayOf(date.toString())
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: error("No effective partner shares for $date")
        return db.rawQuery(
            "SELECT partner_id,percent FROM partner_share_lines WHERE period_id=? ORDER BY partner_id",
            arrayOf(effective)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(PartnerShare(c.getString(0), BigDecimal(c.getString(1))))
            }
        }
    }

    fun withdrawals(month: YearMonth, currency: String): List<DatedPartnerWithdrawal> {
        require(currency in setOf("USD", "TRY", "SYP"))
        return db.rawQuery(
            "SELECT partner_id,actual_date,currency,amount_minor FROM partner_drawings WHERE actual_date>=? AND actual_date<? AND currency=? ORDER BY actual_date,id",
            arrayOf(month.atDay(1).toString(), month.plusMonths(1).atDay(1).toString(), currency)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    DatedPartnerWithdrawal(c.getString(0), LocalDate.parse(c.getString(1)), c.getString(2), c.getLong(3))
                )
            }
        }
    }

    fun monthly(
        month: YearMonth,
        currency: String,
        distributableProfitMinor: Long
    ): List<PartnerMonthLine> {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        val startShares = sharesAt(start)
        require(sharesAt(end) == startShares && db.rawQuery("SELECT count(*) FROM partner_share_periods WHERE effective_date>? AND effective_date<=?", arrayOf(start.toString(), end.toString())).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) == 0L }) {
            "Partner shares changed during this month; profit must be split by effective-date subperiods"
        }
        return PartnerMonthCalculator.allocate(month, currency, distributableProfitMinor, startShares, withdrawals(month, currency))
    }
}
