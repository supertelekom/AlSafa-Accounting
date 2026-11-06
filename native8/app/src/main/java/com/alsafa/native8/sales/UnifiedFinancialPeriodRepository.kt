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
  val maintenanceReturnImpact:BigDecimal=BigDecimal.ZERO,
  val historicalMaintenanceProfit:BigDecimal=BigDecimal.ZERO,
  val nativeMaintenanceProfit:BigDecimal=BigDecimal.ZERO,
  val storeReturnImpact:BigDecimal=BigDecimal.ZERO,
  val importRunId:Long?=null
 ) {
  val totalStoreProfit get()=storeSaleProfit+if(historicalSalesIncluded)historicalStoreProfit else BigDecimal.ZERO
  val totalOnlineProfit get()=saleProfit+if(historicalSalesIncluded)historicalOnlineProfit else BigDecimal.ZERO
  val totalAdjustments get()=(if(historicalSalesIncluded)historicalSaleReturnImpact else BigDecimal.ZERO)+maintenanceReturnImpact+storeReturnImpact-saleReversals
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
  val unresolved get()=(period.unresolvedHistorical+cumulative.unresolvedHistorical+
   if(period.importRunId!=cumulative.importRunId) listOf("تغيرت النسخة المستعادة بين حساب الفترة والتراكمي؛ أعد عرض التقرير") else emptyList()).distinct()
  fun partners():List<PartnerFinancialStatement.Row> {
   check(unresolved.isEmpty()) { unresolved.joinToString("\n") }
   return PartnerFinancialStatement.calculate(period.approvedNet(),cumulative.approvedNet(),period.withdrawals,cumulative.withdrawals)
  }
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
  // Keep verified sources readable; unresolved receipts still block approval, never get reclassified.
  val receiptIssues=sales.nonOnlineProfitSaleIds(end).map {
   "فاتورة $it: قيد ربح تحصيل دون فاتورة مرتبطة أو بنوع بيع غير معروف"
  }
  val compatibility=OriginalStoreProfitCompatibility.read(sales,end)
  val profits=sales.verifiedOnlineProfitsInPeriod(start,end)
   .fold(BigDecimal.ZERO) { acc,row -> acc+decimal(row.recognized_profit) }
  val reversals=sales.verifiedOnlineReversalsInPeriod(start,end)
   .fold(BigDecimal.ZERO) { acc,row -> acc+decimal(row.reversed_profit) }
  val storeDao=requireNotNull(store) { "مستودع أرباح المحل غير موصول؛ لا يمكن عرض إجمالي جزئي مضلل" }
  val storeProfit=(storeDao.inPeriod(start,end)+compatibility.events.filter { it.recognized_at>=start }).fold(BigDecimal.ZERO) { acc,row -> acc+decimal(row.profit_usd) }
  val storeAdjustments=storeDao.adjustmentsInPeriod(start,end).fold(BigDecimal.ZERO) { acc,row -> acc+decimal(row.profit_delta_usd) }
  val expenseAudit=expenses.load(from,to,allowIncomplete=true)
  val history=expenseAudit.source
  val expense=history.expenses.fold(BigDecimal.ZERO) { acc,row -> acc+row.money.usd() }
  val withdrawals=LegacyFinancialRules.partners.associate { partner ->
   partner.id to history.withdrawals.filter { it.partnerId==partner.id }
    .fold(BigDecimal.ZERO) { acc,row -> acc+row.money.usd() }
  }
  val old=historical?.read(from,to)
  val nativeRepairs=maintenance?.read(to)
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
  val issues=(compatibility.unresolved+receiptIssues+old?.unresolved.orEmpty()+expenseAudit.unresolved+nativeRepairs?.audit?.unresolved.orEmpty()+
   (if(old!=null && old.importRunId!=expenseAudit.importRunId) listOf("تغيرت النسخة المستعادة بين قراءة الفواتير والمصاريف؛ أعد عرض التقرير") else emptyList())+
   overlap.map { "Historical/operational sale overlap: $it" }+
   repairOverlap.map { "Historical/operational repair overlap: $it" }+
   sales.uncoveredSales(end).filter { id -> compatibility.events.none { it.sale_id==id } }.map { "Sale without verified profit coverage: $it" }+
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
   historicalMaintenanceProfit=if(includeRepairs) old!!.repairProfitUsd else BigDecimal.ZERO,
   nativeMaintenanceProfit=nativeRepairSummary?.repair ?: BigDecimal.ZERO,
   storeReturnImpact=storeAdjustments,importRunId=old?.importRunId,
   withdrawals=withdrawals,partialNet=profits+storeProfit+storeAdjustments-reversals-expense+historicalSales+repairProfit+repairReturns)
 }
}
