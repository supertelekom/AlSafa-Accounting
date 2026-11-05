package com.alsafa.native8.sales

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/** Partial read-only operational projection; not a complete profit statement. */
class UnifiedFinancialPeriodRepository(
 private val sales:SalesFinancialAuditDao,
 private val expenses:UnifiedExpensePartnerReadRepository,
 private val businessZone:ZoneId,
 private val store:StoreSaleProfitDao?=null,
 private val historical:LegacyHistoricalProfitProjection?=null
) {
 data class Result(
  val saleProfit:BigDecimal,
  val saleReversals:BigDecimal,
  val operatingExpenses:BigDecimal,
  val withdrawals:Map<String,BigDecimal>,
  val partialNet:BigDecimal,
  val complete:Boolean=false,
  val storeSaleProfit:BigDecimal=BigDecimal.ZERO,
  val historicalOnlineProfit:BigDecimal=BigDecimal.ZERO,
  val historicalStoreProfit:BigDecimal=BigDecimal.ZERO,
  val unresolvedHistorical:List<String> = emptyList()
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
  check(sales.nonOnlineProfitEventCount()==0) { "توجد قيود أرباح تحصيل مرتبطة ببيع محل أو فاتورة غير مصنفة؛ يلزم التدقيق قبل تجميع الأرباح" }
  val profits=sales.verifiedOnlineProfitsInPeriod(start,end)
   .fold(BigDecimal.ZERO) { acc,row -> acc+decimal(row.recognized_profit) }
  val reversals=sales.verifiedOnlineReversalsInPeriod(start,end)
   .fold(BigDecimal.ZERO) { acc,row -> acc+decimal(row.reversed_profit) }
  val storeDao=requireNotNull(store) { "مستودع أرباح المحل غير موصول؛ لا يمكن عرض إجمالي جزئي مضلل" }
  val storeProfit=storeDao.inPeriod(start,end).fold(BigDecimal.ZERO) { acc,row -> acc+decimal(row.profit_usd) }
  val storeAdjustments=storeDao.adjustmentsInPeriod(start,end).fold(BigDecimal.ZERO) { acc,row -> acc+decimal(row.profit_delta_usd) }
  val history=expenses.load(from,to).source
  val expense=history.expenses.fold(BigDecimal.ZERO) { acc,row -> acc+row.money.usd() }
  val withdrawals=LegacyFinancialRules.partners.associate { partner ->
   partner.id to history.withdrawals.filter { it.partnerId==partner.id }
    .fold(BigDecimal.ZERO) { acc,row -> acc+row.money.usd() }
  }
  val old=historical?.read(from,to)
  // Do not sum staged historical profits into operational totals until source overlap is reconciled.
  return Result(profits,reversals,storeSaleProfit=storeProfit,operatingExpenses=expense,
   historicalOnlineProfit=old?.onlineProfitUsd ?: BigDecimal.ZERO,
   historicalStoreProfit=old?.storeProfitUsd ?: BigDecimal.ZERO,
   unresolvedHistorical=old?.unresolved ?: listOf("Historical projection not connected"),
   withdrawals=withdrawals,partialNet=profits+storeProfit+storeAdjustments-reversals-expense)
 }
}
