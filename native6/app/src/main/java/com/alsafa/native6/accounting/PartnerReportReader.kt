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
        "CREATE INDEX IF NOT EXISTS partner_drawings_month ON partner_drawings(actual_date,currency,partner_id)",
        "CREATE UNIQUE INDEX IF NOT EXISTS partner_share_effective_unique ON partner_share_periods(effective_date)",
        "CREATE TRIGGER IF NOT EXISTS partner_drawings_no_update BEFORE UPDATE ON partner_drawings BEGIN SELECT RAISE(ABORT,'Immutable partner drawing'); END",
        "CREATE TRIGGER IF NOT EXISTS partner_drawings_no_delete BEFORE DELETE ON partner_drawings BEGIN SELECT RAISE(ABORT,'Immutable partner drawing'); END",
        "CREATE TRIGGER IF NOT EXISTS partner_share_periods_no_update BEFORE UPDATE ON partner_share_periods BEGIN SELECT RAISE(ABORT,'Immutable share schedule'); END",
        "CREATE TRIGGER IF NOT EXISTS partner_share_periods_no_delete BEFORE DELETE ON partner_share_periods BEGIN SELECT RAISE(ABORT,'Immutable share schedule'); END",
        "CREATE TRIGGER IF NOT EXISTS partner_share_lines_no_update BEFORE UPDATE ON partner_share_lines BEGIN SELECT RAISE(ABORT,'Immutable share allocation'); END",
        "CREATE TRIGGER IF NOT EXISTS partner_share_lines_no_delete BEFORE DELETE ON partner_share_lines BEGIN SELECT RAISE(ABORT,'Immutable share allocation'); END"
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

    /**
     * Splits posted monthly profit by event date and the effective share schedule.
     * A dated journal net is required: allocating an aggregate monthly net across
     * mid-month share changes would fabricate partner entitlements.
     */
    fun monthlyFromPostedJournal(month: YearMonth, currency: String): List<PartnerMonthLine> {
        require(currency in setOf("USD", "TRY", "SYP"))
        val from=month.atDay(1).toString()
        val until=month.plusMonths(1).atDay(1).toString()
        // Detect any unregistered historical drawings before presenting a report.
        // Such records must not be silently omitted from a partner's monthly totals.
        val unlinked = db.rawQuery(
            """SELECT count(*) FROM events e JOIN journal j ON j.event_id=e.id
               WHERE e.date>=? AND e.date<? AND j.currency=?
                 AND e.kind='WITHDRAWAL' AND j.account LIKE 'equity:withdrawal:%'
                 AND NOT EXISTS (SELECT 1 FROM partner_drawings d WHERE d.event_id=e.id)""",
            arrayOf(from,until,currency)
        ).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }
        require(unlinked==0L) { "Unlinked partner withdrawals in $month ($currency); audit and backfill first" }
        val invalidDates = db.rawQuery(
            "SELECT count(*) FROM partner_drawings d JOIN events e ON e.id=d.event_id WHERE e.date>=? AND e.date<? AND d.currency=? AND (d.actual_date<>e.date OR e.kind<>?)",
            arrayOf(from,until,currency,"WITHDRAWAL")
        ).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }
        require(invalidDates==0L) { "Invalid partner drawing dates or event types" }
        val profitByDay = db.rawQuery(
            """SELECT e.date,
                 coalesce(sum(CASE
                   WHEN j.account LIKE 'revenue:%' THEN -j.amount
                   WHEN j.account LIKE 'expense:%' THEN -j.amount
                   ELSE 0 END),0) AS profit
               FROM events e JOIN journal j ON j.event_id=e.id
               WHERE e.date>=? AND e.date<? AND j.currency=?
               GROUP BY e.date ORDER BY e.date""",
            arrayOf(from,until,currency)
        ).use { cursor ->
            buildList {
                while(cursor.moveToNext()) add(LocalDate.parse(cursor.getString(0)) to cursor.getLong(1))
            }
        }
        val allocations=linkedMapOf<String,Long>()
        // Collect every schedule active during the month, including partners who leave
        // before month-end. Otherwise their earlier earned profit disappears from reports.
        val changeDates=db.rawQuery(
            "SELECT effective_date FROM partner_share_periods WHERE effective_date>? AND effective_date<? ORDER BY effective_date",
            arrayOf(from,until)
        ).use { cursor ->
            buildList { while(cursor.moveToNext()) add(LocalDate.parse(cursor.getString(0))) }
        }
        (listOf(month.atDay(1)) + changeDates).forEach { day ->
            sharesAt(day).forEach { allocations.putIfAbsent(it.partnerId,0L) }
        }
        for ((day,profit) in profitByDay) {
            val lines=PartnerMonthCalculator.allocate(month,currency,profit,sharesAt(day),emptyList())
            for(line in lines) allocations[line.partnerId]=Math.addExact(allocations[line.partnerId]?:0L,line.allocatedProfitMinor)
        }
        val drawings=withdrawals(month,currency)
        // A drawing can belong to a partner no longer present in the active share schedule.
        drawings.forEach { allocations.putIfAbsent(it.partnerId,0L) }
        val drawingsByPartner=drawings.groupBy { it.partnerId }
        return allocations.map { (partner,profit) ->
            val withdrawn=drawingsByPartner[partner].orEmpty().fold(0L) { total,draw ->
                Math.addExact(total,draw.amountMinor)
            }
            PartnerMonthLine(partner,month,currency,profit,withdrawn,Math.subtractExact(profit,withdrawn))
        }
    }

    fun monthly(
        month: YearMonth,
        currency: String,
        distributableProfitMinor: Long
    ): List<PartnerMonthLine> {
        require(db.rawQuery(
            """SELECT count(*) FROM events e JOIN journal j ON j.event_id=e.id
               WHERE e.date>=? AND e.date<? AND j.currency=?
                 AND e.kind='WITHDRAWAL' AND j.account LIKE 'equity:withdrawal:%'
                 AND NOT EXISTS (SELECT 1 FROM partner_drawings d WHERE d.event_id=e.id)""",
            arrayOf(month.atDay(1).toString(),month.plusMonths(1).atDay(1).toString(),currency)
        ).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }==0L) {
            "Unlinked partner withdrawals in $month ($currency); audit and backfill first"
        }
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        val startShares = sharesAt(start)
        require(sharesAt(end) == startShares && db.rawQuery("SELECT count(*) FROM partner_share_periods WHERE effective_date>? AND effective_date<=?", arrayOf(start.toString(), end.toString())).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) == 0L }) {
            "Partner shares changed during this month; use monthlyFromPostedJournal"
        }
        return PartnerMonthCalculator.allocate(month, currency, distributableProfitMinor, startShares, withdrawals(month, currency))
    }
}
