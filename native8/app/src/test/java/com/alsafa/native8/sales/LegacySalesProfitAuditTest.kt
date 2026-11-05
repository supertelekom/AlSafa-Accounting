package com.alsafa.native8.sales

import org.junit.Assert.*
import org.junit.Test

class LegacySalesProfitAuditTest {
 private fun row(id:String,type:String):LegacySaleEntity =
  LegacySaleEntity(id,"source",1,1,"","","USD","100","0","100","confirmed","",
   """{"saleType":"$type"}""")
 @Test fun historicalChannelsAreCountedButNeverMarkedProfitComplete() {
  val summary=LegacySalesProfitAudit.inspect(listOf(row("a","store"),row("b","online")))
  assertEquals(1,summary.storeInvoices)
  assertEquals(1,summary.onlineInvoices)
  assertFalse(summary.readyForProfit)
 }
 @Test fun unknownHistoricalChannelIsExplicitlyUnresolved() {
  val summary=LegacySalesProfitAudit.inspect(listOf(row("a","unknown")))
  assertEquals(listOf("a"),summary.unresolvedIds)
  assertFalse(summary.readyForProfit)
 }
}
