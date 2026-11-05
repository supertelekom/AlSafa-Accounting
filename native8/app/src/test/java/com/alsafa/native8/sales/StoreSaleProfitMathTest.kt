package com.alsafa.native8.sales

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class StoreSaleProfitMathTest {
 @Test fun storeProfitDoesNotDependOnCashCollection() {
  assertEquals(BigDecimal("40"),StoreSaleProfitMath.calculate(BigDecimal("100"),BigDecimal("60")))
 }
 @Test fun storeLossIsPreserved() {
  assertEquals(BigDecimal("-10"),StoreSaleProfitMath.calculate(BigDecimal("50"),BigDecimal("60")))
 }
 @Test(expected=IllegalArgumentException::class)
 fun invalidNegativeCostRejected() {
  StoreSaleProfitMath.calculate(BigDecimal("100"),BigDecimal("-1"))
 }
}
