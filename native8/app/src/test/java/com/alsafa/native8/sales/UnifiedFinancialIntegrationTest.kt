package com.alsafa.native8.sales

import com.alsafa.native8.maintenance.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/** Executes the production aggregate and readers; DAO/source responses are in memory, not Room. */
class UnifiedFinancialIntegrationTest {
 private fun fixture(name:String)=JSONObject(javaClass.getResourceAsStream("/$name")!!.bufferedReader().use { it.readText() })
 private fun rows(o:JSONObject,k:String)=o.getJSONArray(k).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
 private class Source(val sections:MutableMap<String,List<JSONObject>>):HistoricalFinancialSource {
  var run:Long?=1
  val runs=mutableListOf<Long>()
  override fun latestRunId():Long? { if(runs.isNotEmpty()) run=runs.removeAt(0);return run }
  override fun financialRows(run:Long,section:String)=sections[section].orEmpty()
 }
 private fun source():Source {
  val s=fixture("historical-sales-v359.json");val f=fixture("historical-finance-v359.json")
  return Source(mutableMapOf("sales" to rows(s,"sales"),"repairs" to rows(f,"repairs"),
   "payments" to (rows(s,"payments")+rows(f,"payments")),"returns" to (rows(s,"returns")+rows(f,"returns")),
   "cashTransactions" to rows(s,"cash"),"expenses" to rows(f,"expenses"),"partnerWithdrawals" to rows(f,"withdrawals")))
 }
 private inline fun <reified T> dao(noinline read:(String,Array<out Any?>)->Any?):T =
  Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)) { _,m,a -> read(m.name,a ?: emptyArray()) } as T
 private val zone=ZoneId.of("Asia/Damascus")
 private fun millis(date:String)=LocalDate.parse(date).atTime(12,0).atZone(zone).toInstant().toEpochMilli()
 private fun amount(expected:String,actual:BigDecimal) { assertTrue("$expected != $actual",(expected.toBigDecimal()-actual).abs()<BigDecimal("0.00000001")) }
 private fun rejected(action:()->Unit) { try { action();fail("Incomplete source published") } catch(_:IllegalStateException) {} }
 private data class Setup(val repository:UnifiedFinancialPeriodRepository,val source:Source)
 private fun setup(withNative:Boolean=true,expenseOverlap:Boolean=false,uncovered:Boolean=false,missingCost:Boolean=false):Setup {
  val source=source();val t=millis("2026-11-01")
  fun inside(a:Array<out Any?>)=withNative && t>=a[0] as Long && t<a[1] as Long
  val sales=dao<SalesFinancialAuditDao> { method,a -> when(method) {
   "nonOnlineProfitEventCount" -> 0
   "operationalSaleIds" -> if(withNative) listOf("new-store","new-online") else emptyList<String>()
   "uncoveredSales" -> if(uncovered) listOf("missing-sale") else emptyList<String>()
   "uncoveredOnlinePayments" -> emptyList<String>()
   "verifiedOnlineProfitsInPeriod" -> if(inside(a)) listOf(SaleProfitEvent("pay","new-online",t,"10","100","0","10")) else emptyList<SaleProfitEvent>()
   "verifiedOnlineReversalsInPeriod" -> if(inside(a)) listOf(SaleProfitReversal("reversal","new-online","pay","refund","2","2",t)) else emptyList<SaleProfitReversal>()
   else -> error("Unexpected access: $method")
  } }
  val store=dao<StoreSaleProfitDao> { method,a -> when(method) {
   "inPeriod" -> if(inside(a)) listOf(StoreSaleProfitEvent("new-store",t,"100","60","40")) else emptyList<StoreSaleProfitEvent>()
   "adjustmentsInPeriod" -> if(inside(a)) listOf(StoreProfitAdjustment("adj","new-store",t,"-3","مرتجع")) else emptyList<StoreProfitAdjustment>()
   else -> error("Unexpected access: $method")
  } }
  val expenseId=if(expenseOverlap) source.sections.getValue("expenses").first().getString("id") else "new-expense"
  val costs=dao<OperationalExpensePartnerDao> { method,a ->
   val inPeriod=withNative && "2026-11-01">=a[0] as String && "2026-11-01"<=a[1] as String
   when(method) {
    "expenses" -> if(inPeriod) listOf(OperationalExpenseEntity(expenseId,"2026-11-01","12:00","تشغيل","","USD","7","نقدي","drawer","1",t,"cash-expense")) else emptyList<OperationalExpenseEntity>()
    "withdrawals" -> if(inPeriod) listOf(OperationalPartnerWithdrawalEntity("w1","2026-11-01","12:00","father","USD","2","drawer","1","",t,"cash-w1"),OperationalPartnerWithdrawalEntity("w2","2026-11-01","12:00","me","USD","3","drawer","1","",t,"cash-w2")) else emptyList<OperationalPartnerWithdrawalEntity>()
    else -> error("Unexpected access: $method")
   }
  }
  val raw=JSONObject("""{"id":"native-problem","invoiceNo":433,"status":"تم التسليم","quickServiceName":"صيانة مشكلة","date":"2026-11-01","deliveredDate":"2026-11-01","currency":"USD","price":20,"laborPrice":20,"tax":0,"taxRate":0,"received":20,"cost":0,"usedParts":[],"paymentLines":[{"amount":20,"currency":"USD","fxRate":1,"invoiceCredit":20}]}""")
  val refund=MaintenanceReturnRules.calculate(raw,MaintenanceReturnRules.Request("native-refund","return",-1,BigDecimal.ZERO,BigDecimal(5),false,"",BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,"drawer","2026-11-02","12:00","تسوية"),null,JSONObject().put("USD",1))
  raw.put("nativeReturns",JSONArray().put(refund))
  if(missingCost) raw.remove("cost")
  val repairs=dao<MaintenanceDao> { method,_ -> when(method) {
   "nativeFinancialRecords" -> if(withNative) listOf(MaintenanceRepairEntity("native-problem","native8",433,"2026-11-01","12:00","تم التسليم","","","","","","USD","20","20","2026-11-01",t,raw.toString())) else emptyList<MaintenanceRepairEntity>()
   else -> error("Unexpected access: $method")
  } }
  val cash=dao<LegacyPaymentDao> { method,a -> check(method=="cashboxMovements")
   when(a[0]) {
    "native-problem" -> listOf(RoomCashboxMovement("cash-repair","drawer","USD","20","native-problem",t,""))
    "native-refund" -> listOf(RoomCashboxMovement("cash-refund","drawer","USD","-5","native-refund",millis("2026-11-02"),""))
    else -> error("Unexpected cash ID")
   }
  }
  return Setup(UnifiedFinancialPeriodRepository(sales,UnifiedExpensePartnerReadRepository(source,costs),zone,store,
   LegacyHistoricalProfitProjection(source),NativeMaintenanceProfitRepository(repairs,cash)),source)
 }
 @Test fun archivedInvoicesExpensesAndWithdrawalsFlowThroughActualUnifiedRepository()=runBlocking {
  val r=setup(false).repository.statement("2026-10-01","2026-10-26")
  assertTrue(r.unresolved.toString(),r.cumulative.complete)
  amount("3297.83336318795237672",r.cumulative.approvedNet())
  amount("981.125192629114",r.partners().single { it.partnerId=="father" }.due)
  amount("749.373395009033",r.partners().single { it.partnerId=="me" }.due)
  amount("743.062018865896",r.partners().single { it.partnerId=="brother" }.due)
 }
 @Test fun oldAndNewMergeOnceWithSeparatePeriodAndCumulativeShares()=runBlocking {
  val repository=setup().repository;val r=repository.statement("2026-11-01","2026-11-03")
  assertTrue(r.unresolved.toString(),r.cumulative.complete)
  amount("53",r.period.approvedNet());amount("3350.83336318795237672",r.cumulative.approvedNet())
  for(p in listOf(r.period,r.cumulative)) {
   val displayed=p.historicalStoreProfit+p.storeSaleProfit+p.historicalOnlineProfit+p.saleProfit+
    p.historicalMaintenanceProfit+p.nativeMaintenanceProfit+p.historicalSaleReturnImpact+
    p.maintenanceReturnImpact+p.storeReturnImpact-p.saleReversals-p.operatingExpenses
   assertEquals(0,displayed.compareTo(p.approvedNet()))
  }
  amount("7",r.period.operatingExpenses);amount("20",r.period.nativeMaintenanceProfit)
  amount("0",r.period.historicalMaintenanceProfit);amount("-5",r.period.maintenanceReturnImpact)
  val father=r.partners().single { it.partnerId=="father" }
  amount("21.2",father.periodProfit);amount("2",father.periodWithdrawals)
  amount("1000.325192629114",father.due)
  assertEquals(r,repository.statement("2026-11-01","2026-11-03"))
 }
 @Test fun returnIsRecognizedOnReturnDayNotDeliveryDay()=runBlocking {
  val repository=setup().repository
  amount("58",repository.read("2026-11-01","2026-11-01").approvedNet())
  amount("-5",repository.read("2026-11-02","2026-11-02").approvedNet())
 }
 @Test fun overlapsAndUncoveredInvoicesBlockFinalPartnerDue()=runBlocking {
  for(s in listOf(setup(expenseOverlap=true),setup(uncovered=true),setup(missingCost=true))) {
   val r=s.repository.statement("2026-11-01","2026-11-03")
   assertFalse(r.cumulative.complete);rejected { r.partners() }
  }
 }
 @Test fun changedRestoreBetweenInvoiceAndExpenseReadsCannotPublishNet()=runBlocking {
  val s=setup(false);s.source.runs.addAll(listOf(1L,1L,2L,2L))
  val r=s.repository.read("2026-10-01","2026-10-26")
  assertFalse(r.complete);assertTrue(r.unresolvedHistorical.any { it.contains("تغيرت النسخة") })
  rejected { r.approvedNet() }
 }
 @Test fun periodAndCumulativeMustUseTheSameRestore()=runBlocking {
  val s=setup(false);s.source.runs.addAll(listOf(1L,1L,1L,1L,2L,2L,2L,2L))
  val r=s.repository.statement("2026-10-01","2026-10-26")
  assertTrue(r.period.complete && r.cumulative.complete)
  assertTrue(r.unresolved.any { it.contains("التراكمي") });rejected { r.partners() }
 }
 @Test fun problemMaintenanceIsRevenueOnlyAndNotesAreIrrelevant() {
  val s=source();val repairs=s.sections.getValue("repairs").filter { it.optString("quickServiceName")=="صيانة مشكلة" }
  assertEquals(98,repairs.size)
  repairs.forEach { amount("0",it.get("cost").toString().toBigDecimal());assertEquals(0,it.getJSONArray("usedParts").length()) }
  val payments=s.sections.getValue("payments").filter { p -> repairs.any { it.getString("id")==p.optString("referenceId") } }
  val a=LegacyMaintenanceProfitReader.extract(repairs,payments,emptyList())
  assertTrue(a.unresolved.toString(),a.unresolved.isEmpty());assertTrue(a.events.isNotEmpty())
  a.events.forEach { amount("0",it.costUsd);assertEquals(0,it.revenueUsd.compareTo(it.profitUsd)) }
  val altered=repairs.map { JSONObject(it.toString()).put("notes","ملاحظة مختلفة").put("workDone","") }
  assertEquals(a,LegacyMaintenanceProfitReader.extract(altered,payments,emptyList()))
  val missing=JSONObject(repairs.first().toString());missing.remove("cost")
  assertFalse(LegacyMaintenanceProfitReader.extract(listOf(missing),emptyList(),emptyList()).unresolved.isEmpty())
 }
}
