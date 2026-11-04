package com.alsafa.native8.sales

import org.junit.Assert.assertEquals
import org.junit.Test

class SalesChannelClassificationTest {
 @Test fun knownChannelsRemainDistinct() {
  assertEquals(SalesChannelProfit.Channel.STORE,SalesChannelClassification.requireVerified("store"))
  assertEquals(SalesChannelProfit.Channel.ONLINE,SalesChannelClassification.requireVerified("online"))
 }
 @Test(expected=IllegalStateException::class)
 fun nullChannelIsNotStore() { SalesChannelClassification.requireVerified(null) }
 @Test(expected=IllegalStateException::class)
 fun unexpectedChannelIsNotOnline() { SalesChannelClassification.requireVerified("unknown") }
}
