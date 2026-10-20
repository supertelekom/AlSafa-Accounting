package com.alsafa.native6.accounting

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class PartnerBalanceSummaryCalculatorTest {
    @Test fun selectedMonthDoesNotMergeOldWithdrawals() {
        val september = YearMonth.of(2026, 9)
        val october = YearMonth.of(2026, 10)
        val lines = listOf(
            PartnerMonthLine("owner", september, "USD", 10000, 2000, 8000),
            PartnerMonthLine("owner", october, "USD", 3500, 1200, 2300)
        )
        val result = PartnerBalanceSummaryCalculator.summarize(october, "USD", lines).single()
        assertEquals(2300L, result.selectedMonthRemainderMinor)
        assertEquals(8000L, result.olderUnpaidMinor)
        assertEquals(1200L, result.selectedMonthDrawingsMinor)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateMonthlyEntries() {
        val month = YearMonth.of(2026, 10)
        val line = PartnerMonthLine("owner", month, "USD", 100, 0, 100)
        PartnerBalanceSummaryCalculator.summarize(month, "USD", listOf(line, line))
    }
}
