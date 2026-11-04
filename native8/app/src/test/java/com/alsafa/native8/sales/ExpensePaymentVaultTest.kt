package com.alsafa.native8.sales

import org.junit.Test
import org.junit.Assert.*

class ExpensePaymentVaultTest {
 @Test fun allFourCashboxes() {
  assertEquals("drawer",ExpensePaymentVault.route("نقدي"))
  assertEquals("external",ExpensePaymentVault.route("الخزنة الخارجية"))
  assertEquals("shamcash",ExpensePaymentVault.route("شام كاش"))
  assertEquals("bank",ExpensePaymentVault.route("بنكي"))
 }
 @Test fun unknownDoesNotPostCash() {
  assertNull(ExpensePaymentVault.route("آجل"))
  assertNull(ExpensePaymentVault.route(""))
 }
}
