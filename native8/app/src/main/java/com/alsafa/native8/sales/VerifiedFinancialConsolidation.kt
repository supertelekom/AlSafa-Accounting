package com.alsafa.native8.sales

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Strict finalization boundary: requires source-parity attestation from each
 * imported stream. This is intentionally not called by partial review screens.
 */
object VerifiedFinancialConsolidation {
 enum class RequiredSource { STORE_ARCHIVE, ONLINE_ARCHIVE, REPAIRS, RETURNS, EXPENSES, NEW_SALES }
 data class VerifiedInput(
  val events:List<FinancialSourceReconciliation.Event>,
  val verifiedSources:Set<RequiredSource>,
  val withdrawalsToDateUsd:Map<String,BigDecimal>,
  val from:LocalDate,val to:LocalDate,
  val cumulativeNetToDateUsd:BigDecimal?
 )
 data class Statement(
  val period:FinancialSourceReconciliation.Summary,
  val partners:List<CumulativePartnerProfit.Balance>
 )
 fun finalize(input:VerifiedInput):Statement {
  require(input.verifiedSources.containsAll(RequiredSource.entries)) {
   "Missing verified source streams: ${RequiredSource.entries.toSet()-input.verifiedSources}"
  }
  val cumulative=requireNotNull(input.cumulativeNetToDateUsd) {
   "Cumulative net profit from earliest transaction is required"
  }
  val period=FinancialSourceReconciliation.summarize(input.events,input.from,input.to)
  return Statement(period,CumulativePartnerProfit.calculate(cumulative,input.withdrawalsToDateUsd))
 }
}
