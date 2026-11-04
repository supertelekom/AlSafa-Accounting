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
}
