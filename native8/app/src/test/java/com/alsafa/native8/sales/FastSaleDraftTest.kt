package com.alsafa.native8.sales
import org.junit.Test
import org.junit.Assert.*
import java.math.BigDecimal
class FastSaleDraftTest {
 @Test fun turkishReceiptPreservesActualCurrencyAndDollarProfitBasis() {
  val d=FastSaleDraft.create("source","id",null,"ساعة",BigDecimal("150"),"TRY",BigDecimal("250"),"TRY","نقدي") {a,b->if(a==b) BigDecimal.ONE else BigDecimal("0.02")}
  assertEquals(BigDecimal("5.00"),d.lines.single().unitPrice)
  assertEquals(0,BigDecimal("3").compareTo(d.lines.single().unitCost))
  assertEquals(BigDecimal.ONE,d.lines.single().quantity)
  assertEquals("TRY",d.payments.single().currency);assertEquals(BigDecimal("250"),d.payments.single().amount)
  assertEquals(d.lines.single().unitPrice,d.payments.single().creditedAmount)
  assertEquals(SalesChannelClassification.STORE,d.channel)
 }
 @Test fun shamCashRejectsTryBeforePosting() {
  try{FastSaleDraft.create("s","i",null,"x",BigDecimal.ONE,"USD",BigDecimal.TEN,"TRY","شام كاش"){_,_->BigDecimal.ONE};fail()}catch(_:IllegalArgumentException){}
 }
}
