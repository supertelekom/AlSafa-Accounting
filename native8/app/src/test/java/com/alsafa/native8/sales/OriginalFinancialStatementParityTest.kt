package com.alsafa.native8.sales
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class OriginalFinancialStatementParityTest {
 private fun fixture(name:String)=JSONObject(requireNotNull(javaClass.getResourceAsStream("/$name")).bufferedReader().use { it.readText() })
 private fun rows(f:JSONObject,key:String)=f.getJSONArray(key).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
 @Test fun originalFullNetAndCumulativePartnerBalancesMatchDatedSourceEvents() {
  val s=fixture("historical-sales-v359.json");val f=fixture("historical-finance-v359.json")
  val sales=LegacyHistoricalSalesProfitReader.extractOriginal(rows(s,"sales"),rows(s,"payments"),rows(s,"cash"),rows(s,"returns"))
  val repairs=LegacyMaintenanceProfitReader.extract(rows(f,"repairs"),rows(f,"payments"),rows(f,"returns"))
  val expenses=LegacyExpenseWithdrawalReader.extract(rows(f,"expenses"),false)
  val withdrawals=LegacyExpenseWithdrawalReader.extract(rows(f,"withdrawals"),true)
  assertTrue((sales.unresolved+repairs.unresolved+expenses.unresolved+withdrawals.unresolved).isEmpty())
  val events=sales.events+repairs.events+expenses.entries.map { VerifiedFinancialEventAdapters.expense(it.id,it.date,it.money.usd()) }
  val actual=FinancialSourceReconciliation.summarize(events,LocalDate.parse("0001-01-01"),LocalDate.parse("2026-10-26")).net
  fun n(e:JSONObject,key:String)=e.get(key).toString().toBigDecimal()
  val expected=f.getJSONObject("expected")
  val oracle=rows(s,"expected").fold(BigDecimal.ZERO) { sum,e -> sum+n(e,"revenue")-n(e,"cost")+n(e,"adjustment") }+
   rows(expected,"events").fold(BigDecimal.ZERO) { sum,e -> sum+n(e,"revenue")-n(e,"cost") }+
   rows(expected,"returns").fold(BigDecimal.ZERO) { sum,e -> sum+n(e,"profit") }-
   rows(expected,"expenses").fold(BigDecimal.ZERO) { sum,e -> sum+n(e,"usd") }
  assertTrue((oracle-actual).abs()<BigDecimal("0.00000001"))
  val allWithdrawals=withdrawals.entries.groupBy { it.partnerId!! }.mapValues { (_,r) -> r.fold(BigDecimal.ZERO) { sum,e -> sum+e.money.usd() } }
  val periodNet=FinancialSourceReconciliation.summarize(events,LocalDate.parse("2026-10-01"),LocalDate.parse("2026-10-26")).net
  val periodWithdrawals=withdrawals.entries.filter { it.date>="2026-10-01" }.groupBy { it.partnerId!! }.mapValues { (_,r) -> r.fold(BigDecimal.ZERO) { sum,e -> sum+e.money.usd() } }
  val partners=PartnerFinancialStatement.calculate(periodNet,actual,periodWithdrawals,allWithdrawals)
  for(row in partners) {
   val oracleWithdrawals=rows(expected,"withdrawals").filter { it.getString("partnerId")==row.partnerId }.fold(BigDecimal.ZERO) { sum,e -> sum+n(e,"usd") }
   val expectedDue=oracle*BigDecimal(row.percent).divide(BigDecimal(100))-oracleWithdrawals
   assertTrue((expectedDue-row.due).abs()<BigDecimal("0.00000001"))
   assertNotEquals(0,row.periodProfit.compareTo(row.cumulativeProfit))
  }
 }
}
