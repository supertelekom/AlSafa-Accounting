package com.alsafa.native8.sales

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class SalesChannelProfitTest {
 private fun n(s:String)=BigDecimal(s)
 @Test fun storeProfitBelongsToInvoiceDayEvenWithoutCollection() {
  val invoice=SalesChannelProfit.Invoice("s","2026-11-01",SalesChannelProfit.Channel.STORE,n("100"),n("60"))
  val result=SalesChannelProfit.calculate(listOf(invoice),emptyList(),"2026-11-01","2026-11-01")
  assertEquals(0,result.store.compareTo(n("40")))
  assertEquals(0,result.online.compareTo(BigDecimal.ZERO))
 }
 @Test fun onlineProfitBelongsToActualCollectionDay() {
  val invoice=SalesChannelProfit.Invoice("o","2026-11-01",SalesChannelProfit.Channel.ONLINE,n("100"),n("60"))
  val receipt=SalesChannelProfit.Collection("p","o","2026-11-03",n("50"))
  val invoices=listOf(invoice)
  val payments=listOf(receipt)
  assertEquals(0,SalesChannelProfit.calculate(invoices,payments,"2026-11-01","2026-11-01").online.compareTo(BigDecimal.ZERO))
  assertEquals(0,SalesChannelProfit.calculate(invoices,payments,"2026-11-03","2026-11-03").online.compareTo(n("20")))
 }
 @Test fun partialOnlineReceiptsAllocateCostOnce() {
  val invoice=SalesChannelProfit.Invoice("o","2026-11-01",SalesChannelProfit.Channel.ONLINE,n("100"),n("60"))
  val payments=listOf(
   SalesChannelProfit.Collection("p1","o","2026-11-03",n("40")),
   SalesChannelProfit.Collection("p2","o","2026-11-04",n("60")))
  assertEquals(0,SalesChannelProfit.calculate(listOf(invoice),payments,"2026-11-01","2026-11-30").online.compareTo(n("40")))
 }
 @Test(expected=IllegalArgumentException::class)
 fun duplicatedReceiptCannotDoubleRecognizeProfit() {
  val invoice=SalesChannelProfit.Invoice("o","2026-11-01",SalesChannelProfit.Channel.ONLINE,n("100"),n("60"))
  val payment=SalesChannelProfit.Collection("p","o","2026-11-03",n("40"))
  SalesChannelProfit.calculate(listOf(invoice),listOf(payment,payment),"2026-11-01","2026-11-30")
 }
 @Test(expected=IllegalArgumentException::class)
 fun overCollectionIsRejected() {
  val invoice=SalesChannelProfit.Invoice("o","2026-11-01",SalesChannelProfit.Channel.ONLINE,n("100"),n("60"))
  SalesChannelProfit.calculate(listOf(invoice),
   listOf(SalesChannelProfit.Collection("p","o","2026-11-03",n("101"))),"2026-11-01","2026-11-30")
 }
}
