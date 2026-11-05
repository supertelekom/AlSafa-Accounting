package com.alsafa.native8.sales
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class PartnerFinancialStatementTest {
 private fun n(s:String)=BigDecimal(s)
 @Test fun dueUsesAllHistoryNotSelectedMonth() {
  val rows=PartnerFinancialStatement.calculate(n("100"),n("1000"),mapOf("father" to n("10")),mapOf("father" to n("150")))
  val father=rows.first { it.partnerId=="father" }
  assertEquals(0,father.periodProfit.compareTo(n("40")))
  assertEquals(0,father.cumulativeProfit.compareTo(n("400")))
  assertEquals(0,father.due.compareTo(n("250")))
  assertEquals(listOf(40,35,25),rows.map { it.percent })
 }
 @Test fun withdrawalsDoNotAlterEarnedProfitAndLossIsPreserved() {
  val rows=PartnerFinancialStatement.calculate(n("-100"),n("-200"),emptyMap(),mapOf("me" to n("20")))
  val me=rows.first { it.partnerId=="me" }
  assertEquals(0,me.due.compareTo(n("-90")))
  assertEquals(0,rows.fold(BigDecimal.ZERO) { sum,row -> sum+row.cumulativeProfit }.compareTo(n("-200")))
 }
 @Test(expected=IllegalArgumentException::class) fun externalPartnersCannotEnterCurrentSplit() {
  PartnerFinancialStatement.calculate(n("100"),n("100"),emptyMap(),mapOf("external" to n("1")))
 }
}
