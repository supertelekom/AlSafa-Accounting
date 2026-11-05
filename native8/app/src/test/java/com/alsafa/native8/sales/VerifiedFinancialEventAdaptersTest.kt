package com.alsafa.native8.sales

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class VerifiedFinancialEventAdaptersTest {
 private fun n(value:String)=BigDecimal(value)
 @Test fun historicalCurrencySnapshotsDetermineActualStoreMargin() {
  val event=VerifiedFinancialEventAdapters.store("42","2026-10-10",
   n("100"),n("60"),n("0.02"),n("0.02"))
  assertEquals(0,event.profitUsd.compareTo(n("0.8")))
 }
 @Test fun onlineCollectionIsRecognizedOnReceiptDay() {
  val event=VerifiedFinancialEventAdapters.onlineReceipt("43","payment-1","2026-11-05",n("50"),n("30"))
  assertEquals(LocalDate.parse("2026-11-05"),event.recognizedOn)
  assertEquals(0,event.profitUsd.compareTo(n("20")))
 }
 @Test fun repairExpenseAndReturnEnterNetExactlyOnce() {
  val date="2026-11-05"
  val rows=listOf(
   VerifiedFinancialEventAdapters.repairDelivery("r1",date,n("30"),n("10")),
   VerifiedFinancialEventAdapters.expense("e1",date,n("5")),
   VerifiedFinancialEventAdapters.adjustment("a1",date,n("-2")))
  val total=FinancialSourceReconciliation.summarize(rows,LocalDate.parse(date),LocalDate.parse(date))
  assertEquals(0,total.net.compareTo(n("13")))
 }
 @Test(expected=IllegalArgumentException::class)
 fun missingSourceIdCannotBeAccepted() {
  VerifiedFinancialEventAdapters.store("","2026-11-05",n("100"),n("60"),BigDecimal.ONE,BigDecimal.ONE)
 }
}
