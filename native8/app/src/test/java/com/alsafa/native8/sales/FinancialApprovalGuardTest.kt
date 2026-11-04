package com.alsafa.native8.sales
import org.junit.Test
import org.junit.Assert.*
import java.math.BigDecimal

class FinancialApprovalGuardTest {
 @Test(expected=IllegalStateException::class)
 fun incompleteProfitCannotBePublished() {
  UnifiedFinancialPeriodRepository.Result(
   BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,
   emptyMap(),BigDecimal.TEN
  ).approvedNet()
 }
 @Test fun completeProfitUsesOriginalPartnerShares() {
  val value=UnifiedFinancialPeriodRepository.Result(
   BigDecimal("100"),BigDecimal.ZERO,BigDecimal.ZERO,
   emptyMap(),BigDecimal("100"),true
  )
  val shares=value.approvedPartnerShares()
  assertEquals(BigDecimal("40"),shares["father"])
  assertEquals(BigDecimal("35"),shares["me"])
  assertEquals(BigDecimal("25"),shares["brother"])
 }
 @Test(expected=IllegalStateException::class)
 fun incompletePartnerBalancesCannotBePublished() {
  UnifiedFinancialPeriodRepository.Result(
   BigDecimal("90"),BigDecimal.ZERO,BigDecimal.ZERO,
   mapOf("father" to BigDecimal("10")),BigDecimal("90")
  ).approvedPartnerBalances()
 }
 @Test fun approvedBalancesSubtractWithdrawalsOnly() {
  val result=UnifiedFinancialPeriodRepository.Result(
   BigDecimal("100"),BigDecimal.ZERO,BigDecimal.ZERO,
   mapOf("father" to BigDecimal("7"),"me" to BigDecimal("5")),
   BigDecimal("100"),true)
  val balances=result.approvedPartnerBalances()
  assertEquals(0,balances.getValue("father").compareTo(BigDecimal("33")))
  assertEquals(0,balances.getValue("me").compareTo(BigDecimal("30")))
  assertEquals(0,balances.getValue("brother").compareTo(BigDecimal("25")))
 }

}
