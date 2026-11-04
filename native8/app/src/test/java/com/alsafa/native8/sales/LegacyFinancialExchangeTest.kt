package com.alsafa.native8.sales
import org.junit.Test
import org.junit.Assert.assertEquals
import java.math.BigDecimal

class LegacyFinancialExchangeTest {
 @Test fun dollarConversion() {
  assertEquals(0,LegacyFinancialExchange.usd("150000","SYP","15000").compareTo(BigDecimal.TEN))
  assertEquals(0,LegacyFinancialExchange.usd("225","TRY","45").compareTo(BigDecimal("5")))
  assertEquals(0,LegacyFinancialExchange.usd("95","USD",null).compareTo(BigDecimal("95")))
 }
 @Test(expected=IllegalStateException::class)
 fun missingRate() { LegacyFinancialExchange.usd("100","TRY",null) }
 @Test(expected=IllegalArgumentException::class)
 fun zeroRate() { LegacyFinancialExchange.usd("100","SYP","0") }
}
