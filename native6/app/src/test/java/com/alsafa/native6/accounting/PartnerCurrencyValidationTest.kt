package com.alsafa.native6.accounting

import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class PartnerCurrencyValidationTest {
    private val shares = listOf(
        PartnerShare("father", BigDecimal("40")),
        PartnerShare("owner", BigDecimal("35")),
        PartnerShare("brother", BigDecimal("25"))
    )

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownWithdrawalCurrencyEvenIfOtherCurrencyIsSelected() {
        PartnerMonthCalculator.allocate(
            YearMonth.of(2026, 10), "USD", 10000, shares,
            listOf(DatedPartnerWithdrawal("owner", LocalDate.of(2026, 10, 4), "EUR", 500))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedReportCurrency() {
        PartnerMonthCalculator.allocate(
            YearMonth.of(2026, 10), "EUR", 10000, shares, emptyList()
        )
    }
}
