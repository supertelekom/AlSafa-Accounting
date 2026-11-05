package com.alsafa.native8.sales

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class LegacyFinancialRulesTest {
 private fun n(value:String)=BigDecimal(value)
 private fun usd(value:String)=LegacyFinancialRules.Money(n(value),BigDecimal.ONE)
 private fun sample():LegacyFinancialRules.Source {
  val rules=LegacyFinancialRules
  return LegacyFinancialRules.Source(
   sales=listOf(LegacyFinancialRules.Sale("2026-10-01",false,usd("100"),usd("0"),usd("60")),
    LegacyFinancialRules.Sale("2026-10-01",true,usd("100"),usd("0"),usd("60"))),
   onlineReceipts=listOf(LegacyFinancialRules.Receipt("2026-10-02",usd("100"),usd("0"),usd("35"),n("100"),n("50"))),
   repairReceipts=listOf(LegacyFinancialRules.Receipt("2026-10-02",usd("20"),usd("0"),usd("5"),n("20"),n("20"))),
   returns=listOf(LegacyFinancialRules.Adjustment("2026-10-02",n("-4"))),
   expenses=listOf(LegacyFinancialRules.Expense("2026-10-02",usd("10"))),
   withdrawals=listOf(LegacyFinancialRules.Withdrawal("2026-10-02","father",usd("5")))
  )
 }
 @Test fun storeProfitUsesInvoiceDateAndOnlineUsesCollectionDate() {
  val source=sample()
  assertEquals(0,LegacyFinancialRules.breakdown(source,"2026-10-01","2026-10-01").net.compareTo(n("40")))
  assertEquals(0,LegacyFinancialRules.breakdown(source,"2026-10-02","2026-10-02").net.compareTo(n("33.5")))
 }
 @Test fun withdrawalsDoNotReduceBusinessProfit() {
  val source=sample()
  val total=LegacyFinancialRules.breakdown(source,"2026-10-01","2026-10-02")
  assertEquals(0,total.net.compareTo(n("73.5")))
  val father=LegacyFinancialRules.statement(source,LegacyFinancialRules.partners[0],"2026-10-01","2026-10-02","2026-10-02")
  assertEquals(0,father.periodShare.compareTo(n("29.4")))
  assertEquals(0,father.cumulativeDue.compareTo(n("24.4")))
 }
 @Test fun periodsNeverIncludeFutureWithdrawals() {
  val source=sample().copy(withdrawals=sample().withdrawals+
   LegacyFinancialRules.Withdrawal("2026-11-01","father",usd("100")))
  val father=LegacyFinancialRules.statement(source,LegacyFinancialRules.partners[0],"2026-10-01","2026-10-31","2026-10-31")
  assertEquals(0,father.cumulativeDue.compareTo(n("24.4")))
 }
 @Test fun onlineDiscountAllocatesCostButNotRevenue() {
  val receipt=LegacyFinancialRules.Receipt("2026-10-03",usd("100"),usd("0"),usd("60"),n("100"),n("90"),n("10"))
  assertEquals(0,receipt.profit().compareTo(n("30")))
 }
 @Test fun historicalFxSnapshotIsUsed() {
  val expense=LegacyFinancialRules.Expense("2026-10-03",LegacyFinancialRules.Money(n("100"),n("0.02")))
  val empty=LegacyFinancialRules.Source(emptyList(),emptyList(),emptyList(),emptyList(),listOf(expense),emptyList())
  assertEquals(0,LegacyFinancialRules.breakdown(empty,"2026-10-03","2026-10-03").net.compareTo(n("-2")))
 }
 @Test fun salesReturnRespectsRecognizedRatio() {
  val event=LegacyFinancialRules.SaleReturn("2026-10-04",n("40"),n("20"),n("5"),n("0.5"),n("1"))
  assertEquals(0,event.impactUsd().compareTo(n("-12.5")))
 }
 @Test fun repairReturnRemovesTaxAndUsesSeparateCostRatios() {
  val event=LegacyFinancialRules.RepairReturn("2026-10-04",n("110"),n("22"),
   n("30"),n("10"),n("10"),n("0.5"),n("1"),n("1"))
  assertEquals(0,event.impactUsd().compareTo(n("-75")))
 }
}
