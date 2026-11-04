package com.alsafa.native8.sales

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

/**
 * Hand-calculated fixtures from assets/main.js:
 * grossProfitUsdForRange, onlineSaleReceiptCost, reportProfitBreakdown.
 * These tests validate the pure adapter only; they are NOT Room/JS execution parity.
 */
class LegacyFinancialSourceParityTest {
 private fun n(s:String)=BigDecimal(s)
 private fun money(s:String,rate:String="1")=LegacyFinancialRules.Money(n(s),n(rate))
 private fun source(
  sales:List<LegacyFinancialRules.Sale> = emptyList(),
  online:List<LegacyFinancialRules.Receipt> = emptyList(),
  repairs:List<LegacyFinancialRules.Receipt> = emptyList(),
  adjustments:List<LegacyFinancialRules.Adjustment> = emptyList(),
  expenses:List<LegacyFinancialRules.Expense> = emptyList(),
  withdrawals:List<LegacyFinancialRules.Withdrawal> = emptyList()
 )=LegacyFinancialRules.Source(sales,online,repairs,adjustments,expenses,withdrawals)
 private fun equal(expected:String,actual:BigDecimal)=assertEquals(0,n(expected).compareTo(actual))

 @Test fun storeRecognizedAtSaleDateNotPaymentDate() {
  val s=source(sales=listOf(LegacyFinancialRules.Sale("2026-10-30",false,
   money("225","0.02022222222222222222222222222222222"),
   money("0"),money("125","0.02022222222222222222222222222222222"))))
  equal("0",LegacyFinancialRules.breakdown(s,"2026-10-31","2026-10-31").net)
 }
 @Test fun onlineCollectionUsesDiscountForCostButNotRevenue() {
  val s=source(online=listOf(LegacyFinancialRules.Receipt("2026-11-01",
   money("100"),money("0"),money("60"),n("100"),n("90"),n("10"))))
  equal("30",LegacyFinancialRules.breakdown(s,"2026-11-01","2026-11-01").online)
  equal("0",LegacyFinancialRules.breakdown(s,"2026-10-31","2026-10-31").online)
 }
 @Test fun repairReceiptAndReturnApplyOnTheirOwnDates() {
  val repair=LegacyFinancialRules.Receipt("2026-11-01",
   money("50"),money("0"),money("20"),n("50"),n("50"))
  val adjustment=LegacyFinancialRules.RepairReturn("2026-11-02",
   n("11"),n("0"),n("10"),n("0"),n("10"),n("1"),n("1"),n("1"))
  val s=source(repairs=listOf(repair),
   adjustments=LegacyFinancialRules.returnAdjustments(emptyList(),listOf(adjustment)))
  equal("30",LegacyFinancialRules.breakdown(s,"2026-11-01","2026-11-01").net)
  equal("0",LegacyFinancialRules.breakdown(s,"2026-11-02","2026-11-02").net)
 }
 @Test fun withdrawalsNeverCountAsExpenses() {
  val s=source(sales=listOf(LegacyFinancialRules.Sale("2026-11-01",false,
   money("100"),money("0"),money("40"))),
   expenses=listOf(LegacyFinancialRules.Expense("2026-11-01",money("10"))),
   withdrawals=listOf(LegacyFinancialRules.Withdrawal("2026-11-01","father",money("8"))))
  equal("50",LegacyFinancialRules.breakdown(s,"2026-11-01","2026-11-01").net)
  val father=LegacyFinancialRules.statement(s,LegacyFinancialRules.partners.first(),
   "2026-11-01","2026-11-01","2026-11-01")
  equal("20",father.periodShare)
  equal("12",father.cumulativeDue)
 }
 @Test fun rejectsOverallocatedCollection() {
  assertThrows(IllegalArgumentException::class.java) {
   LegacyFinancialRules.Receipt("2026-11-01",money("100"),money("0"),
    money("60"),n("100"),n("95"),n("10"))
  }
 }
}
