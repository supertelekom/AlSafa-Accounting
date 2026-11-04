package com.alsafa.native8.sales

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacySaleChannelTest {
 @Test fun sourceOnlineSaleIsOnline() {
  assertTrue(LegacySaleChannel.isOnline("""{"id":"s1","saleType":"online"}"""))
 }
 @Test fun sourceStoreSaleIsStore() {
  assertEquals(SalesChannelProfit.Channel.STORE,
   LegacySaleChannel.fromOriginalJson("""{"id":"s2","saleType":"store"}"""))
 }
 @Test fun missingSaleTypeUsesOriginalSourceStoreFallback() {
  assertEquals(SalesChannelProfit.Channel.STORE,
   LegacySaleChannel.fromOriginalJson("""{"id":"s3"}"""))
 }
 @Test(expected=IllegalStateException::class)
 fun unexpectedTypeIsNotSilentlyStore() {
  LegacySaleChannel.fromOriginalJson("""{"id":"s4","saleType":"other"}""")
 }
}
