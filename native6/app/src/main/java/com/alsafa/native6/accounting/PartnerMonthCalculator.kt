package com.alsafa.native6.accounting

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pure monthly partner allocation. No database writes or implicit settlement of old months.
 * Inputs are already posted, single-currency minor-unit amounts.
 */
data class PartnerShare(val partnerId: String, val percentage: BigDecimal)
data class DatedPartnerWithdrawal(
    val partnerId: String,
    val date: LocalDate,
    val currency: String,
    val amountMinor: Long
)
data class PartnerMonthLine(
    val partnerId: String,
    val month: YearMonth,
    val currency: String,
    val allocatedProfitMinor: Long,
    val withdrawnThisMonthMinor: Long,
    val monthRemainderMinor: Long
)

object PartnerMonthCalculator {
    fun allocate(
        month: YearMonth,
        currency: String,
        distributableProfitMinor: Long,
        shares: List<PartnerShare>,
        withdrawals: List<DatedPartnerWithdrawal>
    ): List<PartnerMonthLine> {
        require(currency in setOf("USD", "TRY", "SYP"))
        require(shares.isNotEmpty())
        require(shares.map { it.partnerId }.distinct().size == shares.size)
        require(shares.all { it.partnerId.isNotBlank() && it.percentage >= BigDecimal.ZERO })
        require(shares.fold(BigDecimal.ZERO) { sum, s -> sum + s.percentage }.compareTo(BigDecimal("100")) == 0)
        require(withdrawals.all { it.amountMinor > 0 && it.partnerId in shares.map { s -> s.partnerId } })
        val total = BigDecimal.valueOf(distributableProfitMinor)
        val provisional = shares.map { s ->
            total.multiply(s.percentage).divide(BigDecimal("100"), 0, RoundingMode.DOWN).longValueExact()
        }.toMutableList()
        var remainder = Math.subtractExact(distributableProfitMinor, provisional.fold(0L, Math::addExact))
        // Deterministic largest-fraction distribution; preserves negative-profit totals too.
        val fractions = shares.indices.sortedWith(compareByDescending<Int> { i ->
            total.multiply(shares[i].percentage).divide(BigDecimal("100"))
                .subtract(BigDecimal.valueOf(provisional[i])).abs()
        }.thenBy { shares[it].partnerId })
        var index = 0
        while (remainder != 0L) {
            val i = fractions[index % fractions.size]
            val step = if (remainder > 0) 1L else -1L
            provisional[i] = Math.addExact(provisional[i], step)
            remainder -= step
            index++
        }
        return shares.mapIndexed { i, share ->
            val drawn = withdrawals.asSequence().filter {
                it.partnerId == share.partnerId && it.currency == currency && YearMonth.from(it.date) == month
            }.fold(0L) { sum, w -> Math.addExact(sum, w.amountMinor) }
            PartnerMonthLine(
                share.partnerId, month, currency, provisional[i], drawn,
                Math.subtractExact(provisional[i], drawn)
            )
        }
    }
}
