package com.alsafa.native8.sales

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset

/** Partial read-only operational projection; not a complete profit statement. */
class UnifiedFinancialPeriodRepository(
 private val sales:SalesFinancialAuditDao,
 private val expenses:UnifiedExpensePartnerReadRepository
) {
 data class Result(
  val saleProfit:BigDecimal,
  val saleReversals:BigDecimal,
  val operatingExpenses:BigDecimal,
  val withdrawals:Map<String,BigDecimal>,
  val partialNet:BigDecimal,
  val complete:Boolean=false
 )
 suspend fun read(from:String,to:String):Result {
  val first=LocalDate.parse(from)
  val last=LocalDate.parse(to)
  require(!last.isBefore(first))
  val start=first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
  val end=last.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
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
