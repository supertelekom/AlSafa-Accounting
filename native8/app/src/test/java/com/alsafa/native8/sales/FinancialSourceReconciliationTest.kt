package com.alsafa.native8.sales

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialSourceReconciliationTest {
 private fun n(v:String)=BigDecimal(v)
 private val date=LocalDate.parse("2026-11-05")
 @Test fun combinedHistoricalRepairExpensesAndReturns() {
  val events=listOf(
   FinancialSourceReconciliation.Event("sale:1",FinancialSourceReconciliation.Stream.LEGACY_STORE,date,n("100"),n("60")),
   FinancialSourceReconciliation.Event("online:2:receipt:1",FinancialSourceReconciliation.Stream.LEGACY_ONLINE,date,n("50"),n("30")),
   FinancialSourceReconciliation.Event("repair:3:delivery",FinancialSourceReconciliation.Stream.REPAIR,date,n("20"),n("8")),
   FinancialSourceReconciliation.Event("return:4",FinancialSourceReconciliation.Stream.RETURN,date,n("0"),n("0"),n("-5")),
   FinancialSourceReconciliation.Event("expense:5",FinancialSourceReconciliation.Stream.EXPENSE,date,n("10"),n("0"))
  )
  assertEquals(0,FinancialSourceReconciliation.summarize(events,date,date).net.compareTo(n("57")))
 }
 @Test(expected=IllegalArgumentException::class)
 fun duplicateSourceCannotBeCountedTwice() {
  val event=FinancialSourceReconciliation.Event("sale:1",FinancialSourceReconciliation.Stream.LEGACY_STORE,date,n("100"),n("60"))
  FinancialSourceReconciliation.summarize(listOf(event,event),date,date)
 }
 @Test fun cumulativePartnerSharesSubtractWithdrawalsNotOperatingProfit() {
  val balances=CumulativePartnerProfit.calculate(n("1000"),mapOf("father" to n("100"),"me" to n("50")))
  assertEquals(0,balances[0].dueUsd.compareTo(n("300")))
  assertEquals(0,balances[1].dueUsd.compareTo(n("300")))
  assertEquals(0,balances[2].dueUsd.compareTo(n("250")))
 }
}
