package com.alsafa.native8.sales

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class RefundAllocationMathTest {
 private fun d(v:String)=BigDecimal(v)
 @Test fun distributesAcrossDifferentPaymentCredits() {
  val parts=RefundAllocationMath.allocate(d("35.00"),listOf(
   RefundAllocationMath.Source("usd",d("25.00"),d("10.00")),
   RefundAllocationMath.Source("try",d("20.00"),d("0.00")),
   RefundAllocationMath.Source("syp",d("20.00"),d("0.00"))
  ))
  assertEquals(listOf("usd","try"),parts.map { it.paymentId })
  assertEquals(0,parts[0].baseAmount.compareTo(d("15.00")))
  assertEquals(0,parts[1].baseAmount.compareTo(d("20.00")))
 }
 @Test fun preventsDoubleAllocation() {
  assertThrows(IllegalArgumentException::class.java) {
   RefundAllocationMath.allocate(d("10.01"),listOf(
    RefundAllocationMath.Source("p",d("10.00"),d("0.00"))))
  }
 }
 @Test fun rejectsUnknownRoundingResidual() {
  assertThrows(IllegalArgumentException::class.java) {
   RefundAllocationMath.convertExact(d("0.01"),d("3"))
  }
 }
 @Test fun convertsTryToUsdPrecisely() {
  assertEquals(0,RefundAllocationMath.convertExact(d("10.00"),d("0.02"))
   .compareTo(d("500.00")))
 }
}
