package com.alsafa.native8.sales
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class StoreProfitAdjustmentMathTest {
 private fun n(s:String)=BigDecimal(s)
 @Test fun returnReversesMargin() {
  assertEquals(0,StoreProfitAdjustmentMath.returnedProfitDelta(n("100"),n("60"),n("1")).compareTo(n("-40")))
 }
 @Test fun cancellationOnlyReversesRemainingMargin() {
  assertEquals(0,StoreProfitAdjustmentMath.cancellationDelta(n("40"),listOf(n("-15"))).compareTo(n("-25")))
 }
 @Test fun negativeOriginalProfitCanBeReversed() {
  assertEquals(0,StoreProfitAdjustmentMath.cancellationDelta(n("-10"),emptyList()).compareTo(n("10")))
 }
}
