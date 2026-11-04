package com.alsafa.native8.sales

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class SaleDraftChannelTest {
 @Test fun defaultInvoiceChannelIsStore() {
  val draft=SaleDraft("","",emptyList(),emptyList())
  assertEquals(SalesChannelClassification.STORE,draft.channel)
 }
 @Test fun onlineInvoiceChannelSurvivesDraftCopyWithPayment() {
  val draft=SaleDraft("","",emptyList(),emptyList(),channel=SalesChannelClassification.ONLINE)
  val payment=SalePayment("الدرج","USD",BigDecimal.TEN,"USD",BigDecimal.ONE,BigDecimal.TEN)
  assertEquals(SalesChannelClassification.ONLINE,draft.copy(payments=listOf(payment)).channel)
 }
}
