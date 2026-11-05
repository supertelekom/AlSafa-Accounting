package com.alsafa.native8.sales

import com.alsafa.native8.data.migration.LegacyReadRepository
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Source-derived read-only profit projection for v3.9.359.
 * Sales use the same event reader as the Room archive review.
 * Repairs, expenses and withdrawals share the source-tested read-only adapters.
 */
class LegacyHistoricalProfitProjection(private val legacy:LegacyReadRepository) {
 data class Result(
  val storeProfitUsd:BigDecimal,val repairProfitUsd:BigDecimal,
  val onlineProfitUsd:BigDecimal=BigDecimal.ZERO,
  val returnImpactUsd:BigDecimal,val expensesUsd:BigDecimal,
  val withdrawalsUsd:Map<String,BigDecimal>,
  val unresolved:List<String>,val onlineVerified:Boolean=false,
  val salesUnresolved:List<String> = emptyList(),val salesReturnImpactUsd:BigDecimal=BigDecimal.ZERO,
  val saleIds:Set<String> = emptySet(),val storeCapitalUsd:BigDecimal=BigDecimal.ZERO,
  val repairIds:Set<String> = emptySet(),val repairUnresolved:List<String> = emptyList()
 ) {
  val partialNetUsd get()=storeProfitUsd+onlineProfitUsd+repairProfitUsd+returnImpactUsd-expensesUsd
  val complete get()=onlineVerified && unresolved.isEmpty()
 }
 private fun pages(run:Long,section:String):List<LegacyReadRepository.LegacyRow> {
  val result=mutableListOf<LegacyReadRepository.LegacyRow>()
  var offset=0
  while(true) {
   val page=legacy.page(run,section,offset,500)
   result.addAll(page.rows)
   offset+=page.rows.size
   if(offset>=page.total) return result
   check(page.rows.isNotEmpty()) { "Missing source rows: $section" }
  }
  return result
 }
 fun read(from:String,to:String):Result {
  require(from<=to)
  val run=legacy.latestRunId() ?: return Result(storeProfitUsd=BigDecimal.ZERO,repairProfitUsd=BigDecimal.ZERO,
   returnImpactUsd=BigDecimal.ZERO,expensesUsd=BigDecimal.ZERO,withdrawalsUsd=emptyMap(),unresolved=listOf("No imported source"),salesUnresolved=listOf("No imported source"))
  val salesRows=pages(run,"sales").map { it.fields }
  val payments=pages(run,"payments").map { it.fields }
  val cash=pages(run,"cashTransactions").map { it.fields }
  val returns=pages(run,"returns").map { it.fields }
  val repairs=pages(run,"repairs").map { it.fields }
  val saleAudit=LegacyHistoricalSalesProfitReader.extractOriginal(salesRows,payments,cash,returns)
  val repairAudit=LegacyMaintenanceProfitReader.extract(repairs,payments,returns)
  val expenseAudit=LegacyExpenseWithdrawalReader.extract(pages(run,"expenses").map { it.fields },false)
  val withdrawalAudit=LegacyExpenseWithdrawalReader.extract(pages(run,"partnerWithdrawals").map { it.fields },true)
  val first=java.time.LocalDate.parse(from);val last=java.time.LocalDate.parse(to)
  val saleSummary=FinancialSourceReconciliation.summarize(saleAudit.events,first,last)
  val repairSummary=FinancialSourceReconciliation.summarize(repairAudit.events,first,last)
  fun inPeriod(date:String)=date>=from && date<=to
  val expenses=expenseAudit.entries.filter { inPeriod(it.date) }.fold(BigDecimal.ZERO) { sum,row -> sum+row.money.usd() }
  val withdrawals=LegacyFinancialRules.partners.associate { partner -> partner.id to
   withdrawalAudit.entries.filter { it.partnerId==partner.id && inPeriod(it.date) }
    .fold(BigDecimal.ZERO) { sum,row -> sum+row.money.usd() } }
  val issues=saleAudit.unresolved+repairAudit.unresolved+expenseAudit.unresolved+withdrawalAudit.unresolved
  check(legacy.latestRunId()==run) { "Historical source changed during read" }
  return Result(saleSummary.store,repairSummary.repair,saleSummary.online,
   saleSummary.returns+repairSummary.returns,expenses,withdrawals,issues,
   onlineVerified=saleAudit.unresolved.isEmpty(),salesUnresolved=saleAudit.unresolved,
   salesReturnImpactUsd=saleSummary.returns,saleIds=salesRows.map { it.optString("id") }.toSet(),
   storeCapitalUsd=saleAudit.events.filter { it.stream==FinancialSourceReconciliation.Stream.LEGACY_STORE &&
    inPeriod(it.recognizedOn.toString()) }.fold(BigDecimal.ZERO) { sum,event -> sum+event.costUsd },
   repairIds=repairs.map { it.optString("id") }.toSet(),repairUnresolved=repairAudit.unresolved)
 }
}
