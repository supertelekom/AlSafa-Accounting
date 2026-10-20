package com.alsafa.native6.accounting

import java.time.YearMonth

data class PartnerBalanceSummary(
    val partnerId: String,
    val currency: String,
    val selectedMonth: YearMonth,
    val selectedMonthProfitMinor: Long,
    val selectedMonthDrawingsMinor: Long,
    val selectedMonthRemainderMinor: Long,
    val olderUnpaidMinor: Long
)

/**
 * Older months are shown as a separate informational balance.
 * Drawings never automatically settle an earlier month.
 */
object PartnerBalanceSummaryCalculator {
    fun summarize(
        selected: YearMonth,
        currency: String,
        history: List<PartnerMonthLine>
    ): List<PartnerBalanceSummary> {
        require(history.all { it.currency == currency && it.month <= selected })
        require(history.groupBy { it.partnerId to it.month }.values.all { it.size == 1 }) {
            "Duplicate partner/month report rows"
        }
        val current = history.filter { it.month == selected }
        return current.map { row ->
            val older = history.asSequence()
                .filter { it.partnerId == row.partnerId && it.month < selected }
                .fold(0L) { total, prior -> Math.addExact(total, prior.monthRemainderMinor) }
            PartnerBalanceSummary(
                row.partnerId, currency, selected,
                row.allocatedProfitMinor, row.withdrawnThisMonthMinor,
                row.monthRemainderMinor, older
            )
        }
    }
}
