package com.alsafa.native8.sales

import org.junit.Assert.*
import org.junit.Test

class MaintenanceProfitAuditTest {
 @Test(expected=IllegalStateException::class)
 fun deliveredInvoicesAloneDoNotAuthorizeProfit() {
  MaintenanceProfitAuditRepository.Audit(emptyList(),emptyList()).requireComplete()
 }
 @Test fun missingCostOrReceiptCannotAuthorizeProfit() {
  val audit=MaintenanceProfitAuditRepository.Audit(emptyList(),listOf("repair-432"))
  assertFalse(audit.complete)
  assertEquals(listOf("repair-432"),audit.unresolvedIds)
 }
}
