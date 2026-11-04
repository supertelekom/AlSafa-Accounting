package com.alsafa.native8.sales

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/** Partial read-only operational projection; not a complete profit statement. */
class UnifiedFinancialPeriodRepository(
 private val sales:SalesFinancialAuditDao,
 private val expenses:UnifiedExpensePartnerReadRepository,
 private val businessZone:ZoneId
) {
 data class Result(
  val saleProfit:BigDecimal,
  val saleReversals:BigDecimal,
  val operatingExpenses:BigDecimal,
  val withdrawals:Map<String,BigDecimal>,
  val partialNet:BigDecimal,
  val complete:Boolean=false
 ) {
  fun approvedNet():BigDecimal {
   check(complete) { "Incomplete financial sources: do not publish profit" }
   return partialNet
  }
  fun approvedPartnerShares():Map<String,BigDecimal> {
   val net=approvedNet()
   return LegacyFinancialRules.partners.associate { partner ->
    partner.id to net.multiply(BigDecimal(partner.share)).divide(BigDecimal(100))
   }
  }
  fun approvedPartnerBalances():Map<String,BigDecimal> =
   approvedPartnerShares().mapValues { (id,share) -> share-(withdrawals[id] ?: BigDecimal.ZERO) }
 }
 suspend fun read(from:String,to:String):Result {
  val first=LocalDate.parse(from)
  val last=LocalDate.parse(to)
  require(!last.isBefore(first))
  val window=FinancialPeriodGuard.window(from,to,businessZone)
  val start=window.startInclusive
  val end=window.endExclusive
  fun decimal(s:String)=s.toBigDecimal()
  val profits=sales.profitEventsInPeriod(start,end)
   .fold(BigDecimal.ZERO) { acc,row -> acc+decimal(row.recognized_profit) }
  val reversals=sales.profitReversalsInPeriod(start,end)
   .fold(BigDecimal.ZERO) { acc,row -> acc+decimal(row.reversed_profit) }
  val history=expenses.load(from,to).source
  val expense=history.expenses.fold(BigDecimal.ZERO) { acc,row -> acc+row.money.usd() }
  val withdrawals=LegacyFinancialRules.partners.associate { partner ->
   partner.id to history.withdrawals.filter { it.partnerId==partner.id }
    .fold(BigDecimal.ZERO) { acc,row -> acc+row.money.usd() }
  }
  return Result(profits,reversals,expense,withdrawals,profits-reversals-expense)
 }
}
