package com.alsafa.native8.sales

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class VerifiedFinancialConsolidationTest {
 private val day=LocalDate.parse("2026-11-05")
 @Test(expected=IllegalArgumentException::class)
 fun partialArchiveCannotBePublishedAsFinalProfit() {
  VerifiedFinancialConsolidation.finalize(
   VerifiedFinancialConsolidation.VerifiedInput(emptyList(),emptySet(),emptyMap(),day,day,BigDecimal.ZERO))
 }
 @Test(expected=IllegalArgumentException::class)
 fun partnerBalancesRequireCumulativeProfit() {
  VerifiedFinancialConsolidation.finalize(
   VerifiedFinancialConsolidation.VerifiedInput(emptyList(),
    VerifiedFinancialConsolidation.RequiredSource.values().toSet(),emptyMap(),day,day,null))
 }
 @Test fun verifiedStatementUsesCumulativeNotPeriodNet() {
  val event=FinancialSourceReconciliation.Event("store:1",
   FinancialSourceReconciliation.Stream.LEGACY_STORE,day,BigDecimal("100"),BigDecimal("60"))
  val result=VerifiedFinancialConsolidation.finalize(
   VerifiedFinancialConsolidation.VerifiedInput(listOf(event),
    VerifiedFinancialConsolidation.RequiredSource.values().toSet(),
    mapOf("father" to BigDecimal("10")),day,day,BigDecimal("1000")))
  assertEquals(0,result.period.net.compareTo(BigDecimal("40")))
  assertEquals(0,result.partners[0].dueUsd.compareTo(BigDecimal("390")))
 }
}
