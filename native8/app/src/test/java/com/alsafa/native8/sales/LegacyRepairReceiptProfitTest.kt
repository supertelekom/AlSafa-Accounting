package com.alsafa.native8.sales

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class LegacyRepairReceiptProfitTest {
 private fun n(value:String)=BigDecimal(value)
 @Test fun partialCollectionRecognizesProportionalNetAndCost() {
  val event=LegacyRepairReceiptProfit.Receipt("repair-1","2026-11-05",
   n("50"),n("100"),n("10"),n("30"),BigDecimal.ONE)
  assertEquals(0,event.profitUsd().compareTo(n("30")))
 }
 @Test fun twoCollectionsRecognizeCostWithoutDoubleCounting() {
  val a=LegacyRepairReceiptProfit.Receipt("repair-1","2026-11-05",
   n("25"),n("100"),n("0"),n("40"),BigDecimal.ONE)
  val b=a.copy(date="2026-11-06",amount=n("75"))
  assertEquals(0,LegacyRepairReceiptProfit.sum(listOf(a,b)).compareTo(n("60")))
 }
 @Test fun receiptUsesItsOwnHistoricalExchangeRate() {
  val event=LegacyRepairReceiptProfit.Receipt("repair-1","2026-11-05",
   n("500"),n("1000"),n("0"),n("200"),n("0.02"))
  assertEquals(0,event.profitUsd().compareTo(n("8")))
 }
 @Test fun costUsesOneUnitDenominatorForZeroInvoiceTotal() {
  val event=LegacyRepairReceiptProfit.Receipt("repair-1","2026-11-05",
   n("1"),n("0"),n("0"),n("4"),BigDecimal.ONE)
  assertEquals(0,event.profitUsd().compareTo(n("-4")))
 }
}
