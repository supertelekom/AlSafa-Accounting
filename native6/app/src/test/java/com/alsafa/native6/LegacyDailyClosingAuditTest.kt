package com.alsafa.native6

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyDailyClosingAuditTest {
    @Test fun identifiesMovementDayWithoutClosing() {
        val backup="""{"data":{"dailyClosings":[{"date":"2026-10-01"}],"cashTransactions":[{"date":"2026-10-02","vault":"drawer","currency":"USD","type":"income","amount":5}]}}"""
        val result=LegacyDailyClosingAudit.inspect(backup)
        assertEquals("2026-10-02",result.getJSONArray("movementDaysWithoutClosing").getString(0))
        assertEquals("NONE",result.getString("posting"))
    }
    @Test fun detectsDuplicateClosingDates() {
        val backup="""{"data":{"dailyClosings":[{"date":"2026-10-01"},{"date":"2026-10-01"}]}}"""
        val result=LegacyDailyClosingAudit.inspect(backup)
        assertEquals("2026-10-01",result.getJSONArray("daysWithMultipleClosings").getString(0))
    }
    @Test fun extractsActualDrawerCarryForward() {
        val backup="""{"data":{"dailyClosings":[{"date":"2026-10-01","drawerActual":{"USD":25,"TRY":100},"drawerTransfer":{"TRY":100},"drawerNext":{"USD":25,"TRY":0},"drawerDifference":{"USD":0}}]}}"""
        val report=LegacyDailyClosingAudit.inspect(backup)
        val snapshot=report.getJSONArray("closingBalanceSnapshots").getJSONObject(0)
        assertEquals("25",snapshot.getJSONObject("drawerNext").getString("USD"))
        assertEquals("100",snapshot.getJSONObject("drawerTransfer").getString("TRY"))
        assertEquals(0,report.getJSONArray("invalidClosingBalanceFields").length())
    }
}
