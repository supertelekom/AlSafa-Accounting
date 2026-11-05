package com.alsafa.native8.sales

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class LegacyHistoricalSalesProfitReaderTest {
 private fun sale(id:String,channel:String)=LegacySaleEntity(id,"source",1,1,"","","USD",
  "100","0","100","confirmed","",
  """{"id":"$id","saleType":"$channel","date":"2026-10-10","currency":"USD","total":100,"tax":0,"items":[{"qty":1,"cost":60}],"rateSnapshot":{"USD":1,"TRY":50,"SYP":140}}""")
 @Test fun storeProfitUsesOriginalCost() {
  val a=LegacyHistoricalSalesProfitReader.extract(listOf(sale("s1","store")),emptyList())
  assertTrue(a.unresolved.isEmpty())
  assertEquals(0,a.events.single().profitUsd.compareTo(BigDecimal("40")))
 }
 @Test fun onlineProfitOnlyOnDatedReceipt() {
  val payment=JSONObject("""{"id":"p1","referenceType":"sale","referenceId":"s1","date":"2026-10-11","currency":"USD","amount":50,"rateSnapshot":{"USD":1,"TRY":50,"SYP":140}}""")
  val a=LegacyHistoricalSalesProfitReader.extract(listOf(sale("s1","online")),listOf(payment))
  assertTrue(a.unresolved.isEmpty())
  assertEquals(0,a.events.single().profitUsd.compareTo(BigDecimal("20")))
  assertEquals("2026-10-11",a.events.single().recognizedOn.toString())
 }
}
