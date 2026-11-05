package com.alsafa.native8.sales

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/** Complete only when every original/operational source is covered and disjoint. Read-only. */
class UnifiedFinancialPeriodRepository(
 private val sales:SalesFinancialAuditDao,
 private val expenses:UnifiedExpensePartnerReadRepository,
 private val businessZone:ZoneId,
 private val store:StoreSaleProfitDao?=null,
 private val historical:LegacyHistoricalProfitProjection?=null,
 private val maintenance:NativeMaintenanceProfitRepository?=null
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
  val unresolvedHistorical:List<String> = emptyList(),
  val historicalSaleReturnImpact:BigDecimal=BigDecimal.ZERO,
  val historicalSalesIncluded:Boolean=false,
  val maintenanceProfit:BigDecimal=BigDecimal.ZERO,
  val maintenanceReturnImpact:BigDecimal=BigDecimal.ZERO
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

 }
 data class Statement(val from:String,val to:String,val period:Result,val cumulative:Result) {
  val unresolved get()=(period.unresolvedHistorical+cumulative.unresolvedHistorical).distinct()
  fun partners():List<PartnerFinancialStatement.Row> = PartnerFinancialStatement.calculate(
   period.approvedNet(),cumulative.approvedNet(),period.withdrawals,cumulative.withdrawals)
 }
 suspend fun statement(from:String,to:String):Statement {
  val period=read(from,to)
  val cumulative=if(from=="0001-01-01") period else read("0001-01-01",to)
  return Statement(from,to,period,cumulative)
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
  val expenseAudit=expenses.load(from,to,allowIncomplete=true)
  val history=expenseAudit.source
  val expense=history.expenses.fold(BigDecimal.ZERO) { acc,row -> acc+row.money.usd() }
  val withdrawals=LegacyFinancialRules.partners.associate { partner ->
   partner.id to history.withdrawals.filter { it.partnerId==partner.id }
    .fold(BigDecimal.ZERO) { acc,row -> acc+row.money.usd() }
  }
  val old=historical?.read(from,to)
  val nativeRepairs=maintenance?.read()
  val overlap=old?.saleIds.orEmpty().intersect(sales.operationalSaleIds().toSet())
  val repairOverlap=old?.repairIds.orEmpty().intersect(nativeRepairs?.ids.orEmpty())
  val includeSales=old!=null && old.salesUnresolved.isEmpty() && overlap.isEmpty()
  val historicalSales=if(includeSales) old!!.storeProfitUsd+old.onlineProfitUsd+old.salesReturnImpactUsd else BigDecimal.ZERO
  val nativeRepairSummary=nativeRepairs?.let { FinancialSourceReconciliation.summarize(it.audit.events,first,last) }
  val includeRepairs=old!=null && old.repairUnresolved.isEmpty() && repairOverlap.isEmpty()
  val repairProfit=(if(includeRepairs) old!!.repairProfitUsd else BigDecimal.ZERO)+
   (nativeRepairSummary?.repair ?: BigDecimal.ZERO)
  val repairReturns=(if(includeRepairs) old!!.returnImpactUsd-old.salesReturnImpactUsd else BigDecimal.ZERO)+
   (nativeRepairSummary?.returns ?: BigDecimal.ZERO)
  val issues=(old?.unresolved.orEmpty()+expenseAudit.unresolved+nativeRepairs?.audit?.unresolved.orEmpty()+
   overlap.map { "Historical/operational sale overlap: $it" }+
   repairOverlap.map { "Historical/operational repair overlap: $it" }+
   sales.uncoveredSales(end).map { "Sale without verified profit coverage: $it" }+
   sales.uncoveredOnlinePayments(end).map { "Online receipt without profit event: $it" }+
   (if(old==null) listOf("Historical projection not connected") else emptyList())+
   (if(nativeRepairs==null) listOf("Maintenance projection not connected") else emptyList())).distinct()
  // Expense history is already included in expenseAudit. Never subtract old.expensesUsd again.
  return Result(profits,reversals,storeSaleProfit=storeProfit,operatingExpenses=expense,
   historicalOnlineProfit=old?.onlineProfitUsd ?: BigDecimal.ZERO,
   historicalStoreProfit=old?.storeProfitUsd ?: BigDecimal.ZERO,
   unresolvedHistorical=issues,
   historicalSaleReturnImpact=old?.salesReturnImpactUsd ?: BigDecimal.ZERO,
   historicalSalesIncluded=includeSales,complete=issues.isEmpty(),
   maintenanceProfit=repairProfit,maintenanceReturnImpact=repairReturns,
   withdrawals=withdrawals,partialNet=profits+storeProfit+storeAdjustments-reversals-expense+historicalSales+repairProfit+repairReturns)
 }
}
