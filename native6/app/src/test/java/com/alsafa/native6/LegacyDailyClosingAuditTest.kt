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
}
