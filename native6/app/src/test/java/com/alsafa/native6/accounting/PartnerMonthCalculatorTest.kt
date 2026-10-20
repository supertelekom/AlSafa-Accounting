package com.alsafa.native6.accounting

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class PartnerMonthCalculatorTest {
    private val shares = listOf(
        PartnerShare("father", BigDecimal("40")),
        PartnerShare("owner", BigDecimal("35")),
        PartnerShare("brother", BigDecimal("25"))
    )

    @Test fun monthlyWithdrawalsDoNotLeakIntoAdjacentMonths() {
        val withdrawals = listOf(
            DatedPartnerWithdrawal("owner", LocalDate.of(2026, 9, 30), "USD", 3000),
            DatedPartnerWithdrawal("owner", LocalDate.of(2026, 10, 1), "USD", 1200),
            DatedPartnerWithdrawal("owner", LocalDate.of(2026, 10, 2), "TRY", 900)
        )
        val result = PartnerMonthCalculator.allocate(YearMonth.of(2026, 10), "USD", 10000, shares, withdrawals)
        assertEquals(3500L, result.single { it.partnerId == "owner" }.allocatedProfitMinor)
        assertEquals(1200L, result.single { it.partnerId == "owner" }.withdrawnThisMonthMinor)
        assertEquals(2300L, result.single { it.partnerId == "owner" }.monthRemainderMinor)
        assertEquals(10000L, result.sumOf { it.allocatedProfitMinor })
    }

    @Test fun roundingPreservesTotalIncludingLosses() {
        val thirds = listOf("a", "b", "c").map { PartnerShare(it, BigDecimal("100").divide(BigDecimal("3"), 12, java.math.RoundingMode.HALF_UP)) }
        // Percentages must total exactly 100: use explicit remainder.
        val exact = listOf(PartnerShare("a", BigDecimal("33.33")), PartnerShare("b", BigDecimal("33.33")), PartnerShare("c", BigDecimal("33.34")))
        for (amount in listOf(1L, -1L, 101L, -101L)) {
            val result = PartnerMonthCalculator.allocate(YearMonth.of(2026, 10), "USD", amount, exact, emptyList())
            assertEquals(amount, result.sumOf { it.allocatedProfitMinor })
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidShareTotal() {
        PartnerMonthCalculator.allocate(YearMonth.of(2026, 10), "USD", 100, shares.dropLast(1), emptyList())
    }
}
