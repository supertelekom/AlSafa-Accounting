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

 @org.junit.Test fun multipleItemsAllocateExactTotalAndRetainCosts() {
  val items=listOf(FastSaleDraft.Item("s","a",null,"A",java.math.BigDecimal("2"),java.math.BigDecimal("10"),"USD"),FastSaleDraft.Item("s","b",null,"B",java.math.BigDecimal("100"),java.math.BigDecimal("500"),"TRY"))
  val draft=FastSaleDraft.createMany(items,java.math.BigDecimal("17.33"),"USD","نقدي") { from,to -> if(from==to)java.math.BigDecimal.ONE else java.math.BigDecimal("0.02") }
  org.junit.Assert.assertEquals(java.math.BigDecimal("17.33"),draft.lines.map{it.unitPrice}.reduce(java.math.BigDecimal::add))
  org.junit.Assert.assertEquals(java.math.BigDecimal("8.66"),draft.lines[0].unitPrice)
  org.junit.Assert.assertEquals(java.math.BigDecimal("8.67"),draft.lines[1].unitPrice)
  org.junit.Assert.assertEquals(0,draft.lines[1].unitCost.compareTo(java.math.BigDecimal("2")))
  org.junit.Assert.assertEquals(2,draft.lines.size)
 }
}
