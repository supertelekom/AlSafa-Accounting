package com.alsafa.native8.sales

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class LegacyFinancialRulesTest {
 private fun n(value:String)=BigDecimal(value)
 private fun usd(value:String)=LegacyFinancialRules.Money(n(value),BigDecimal.ONE)
 private fun sample():LegacyFinancialRules.Source {
  val rules=LegacyFinancialRules
  return rules.Source(
   sales=listOf(rules.Sale("2026-10-01",false,usd("100"),usd("0"),usd("60")),
    rules.Sale("2026-10-01",true,usd("100"),usd("0"),usd("60"))),
   onlineReceipts=listOf(rules.Receipt("2026-10-02",usd("100"),usd("0"),usd("35"),n("100"),n("50"))),
   repairReceipts=listOf(rules.Receipt("2026-10-02",usd("20"),usd("0"),usd("5"),n("20"),n("20"))),
   returns=listOf(rules.Adjustment("2026-10-02",n("-4"))),
   expenses=listOf(rules.Expense("2026-10-02",usd("10"))),
   withdrawals=listOf(rules.Withdrawal("2026-10-02","father",usd("5")))
  )
 }
 @Test fun storeProfitUsesInvoiceDateAndOnlineUsesCollectionDate() {
  val source=sample()
  assertEquals(0,LegacyFinancialRules.breakdown(source,"2026-10-01","2026-10-01").net.compareTo(n("40")))
  assertEquals(0,LegacyFinancialRules.breakdown(source,"2026-10-02","2026-10-02").net.compareTo(n("16")))
 }
 @Test fun withdrawalsDoNotReduceBusinessProfit() {
  val source=sample()
  val total=LegacyFinancialRules.breakdown(source,"2026-10-01","2026-10-02")
  assertEquals(0,total.net.compareTo(n("56")))
  val father=LegacyFinancialRules.statement(source,LegacyFinancialRules.partners[0],"2026-10-01","2026-10-02","2026-10-02")
  assertEquals(0,father.periodShare.compareTo(n("22.4")))
  assertEquals(0,father.cumulativeDue.compareTo(n("17.4")))
 }
 @Test fun periodsNeverIncludeFutureWithdrawals() {
  val source=sample().copy(withdrawals=sample().withdrawals+
   LegacyFinancialRules.Withdrawal("2026-11-01","father",usd("100")))
  val father=LegacyFinancialRules.statement(source,LegacyFinancialRules.partners[0],"2026-10-01","2026-10-31","2026-10-31")
  assertEquals(0,father.cumulativeDue.compareTo(n("17.4")))
 }
}
