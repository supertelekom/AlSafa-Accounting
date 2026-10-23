package com.alsafa.native6.accounting

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** Regression coverage for partner losses, currency separation and calendar boundaries. */
class PartnerMonthEdgeCasesTest {
    private val shares = listOf(
        PartnerShare("father", BigDecimal("40")),
        PartnerShare("owner", BigDecimal("35")),
        PartnerShare("brother", BigDecimal("25"))
    )

    @Test fun negativeProfitIsAllocatedWithoutCreatingMoney() {
        for (loss in listOf(-1L, -2L, -101L, -10001L)) {
            val rows = PartnerMonthCalculator.allocate(
                YearMonth.of(2026, 10), "USD", loss, shares, emptyList()
            )
            assertEquals(loss, rows.sumOf { it.allocatedProfitMinor })
            assertEquals(loss, rows.sumOf { it.monthRemainderMinor })
        }
    }

    @Test fun decemberAndJanuaryWithdrawalsStayInTheirOwnMonths() {
        val withdrawals = listOf(
            DatedPartnerWithdrawal("owner", LocalDate.of(2025, 12, 31), "USD", 200L),
            DatedPartnerWithdrawal("owner", LocalDate.of(2026, 1, 1), "USD", 300L),
            DatedPartnerWithdrawal("owner", LocalDate.of(2026, 1, 1), "TRY", 900L)
        )
        val january = PartnerMonthCalculator.allocate(
            YearMonth.of(2026, 1), "USD", 1000L, shares, withdrawals
        )
        assertEquals(300L, january.single { it.partnerId == "owner" }.withdrawnThisMonthMinor)
        val december = PartnerMonthCalculator.allocate(
            YearMonth.of(2025, 12), "USD", 1000L, shares, withdrawals
        )
        assertEquals(200L, december.single { it.partnerId == "owner" }.withdrawnThisMonthMinor)
    }

    @Test fun olderBalancesRemainSeparateFromSelectedMonth() {
        val september = PartnerMonthCalculator.allocate(
            YearMonth.of(2026, 9), "USD", 10000L, shares, emptyList()
        )
        val october = PartnerMonthCalculator.allocate(
            YearMonth.of(2026, 10), "USD", 20000L, shares,
            listOf(DatedPartnerWithdrawal("owner", LocalDate.of(2026, 10, 3), "USD", 1000L))
        )
        val summary = PartnerBalanceSummaryCalculator.summarize(
            YearMonth.of(2026, 10), "USD", september + october
        ).single { it.partnerId == "owner" }
        assertEquals(3500L, summary.olderUnpaidMinor)
        assertEquals(7000L, summary.selectedMonthProfitMinor)
        assertEquals(1000L, summary.selectedMonthDrawingsMinor)
        assertEquals(6000L, summary.selectedMonthRemainderMinor)
    }
}
