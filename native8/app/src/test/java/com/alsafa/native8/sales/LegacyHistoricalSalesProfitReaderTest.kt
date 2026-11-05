package com.alsafa.native8.sales

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class LegacyHistoricalSalesProfitReaderTest {
 private fun sale(id:String,channel:String)=LegacySaleEntity(id,"source",1,1,"","","USD",
  "100","0","100","confirmed","",
  """{"id":"$id","saleType":"$channel","date":"2026-10-10","currency":"USD","total":100,"paid":0,"tax":0,"items":[{"qty":1,"cost":60}],"rateSnapshot":{"USD":1,"TRY":50,"SYP":140}}""")
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

 private fun raw(channel:String="online")=JSONObject(sale("s1",channel).original_json)
 private fun payment(id:String="p1",amount:Int=50)=JSONObject("""{"id":"$id","referenceType":"sale","referenceId":"s1","date":"2026-10-11","currency":"USD","amount":$amount}""")
 @Test fun originalBackupMatchesMainJsEventByEvent() {
  val fixture=JSONObject(requireNotNull(javaClass.getResourceAsStream("/historical-sales-v359.json"))
   .bufferedReader().use { it.readText() })
  fun rows(key:String)=fixture.getJSONArray(key).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
  val result=LegacyHistoricalSalesProfitReader.extractOriginal(rows("sales"),rows("payments"),rows("cash"),rows("returns"))
  assertEquals(emptyList<String>(),result.unresolved)
  val expected=rows("expected")
  assertEquals(114,expected.size)
  assertEquals(expected.size,result.events.size)
  val byKey=result.events.associateBy { it.sourceKey }
  for(e in expected) {
   val actual=requireNotNull(byKey[e.getString("key")])
   assertEquals(e.getString("date"),actual.recognizedOn.toString())
   assertEquals(e.getString("stream"),actual.stream.name)
   for((key,value) in listOf("revenue" to actual.revenueUsd,"cost" to actual.costUsd,"adjustment" to actual.profitAdjustmentUsd))
    assertTrue("${actual.sourceKey}: $key",(value-e.get(key).toString().toBigDecimal()).abs()<BigDecimal("0.00000001"))
  }
 }
 @Test fun openingCashSubtractsSameDayExplicitPayment() {
  val s=raw().put("paid",100)
  val p=payment().put("date","2026-10-10")
  val cash=JSONObject("""{"id":"c1","type":"income","referenceType":"sale","referenceId":"s1","date":"2026-10-10","currency":"USD","amount":70}""")
  val a=LegacyHistoricalSalesProfitReader.extractOriginal(listOf(s),listOf(p),listOf(cash),emptyList())
  assertTrue(a.unresolved.isEmpty())
  assertEquals(2,a.events.size)
  assertEquals(0,a.events.first().profitUsd.compareTo(BigDecimal("8")))
 }
 @Test fun receiptUsesOwnFxAndInvoiceCurrencyAmount() {
  val s=raw().put("currency","TRY").put("rateSnapshot",JSONObject("""{"TRY":50}"""))
  val p=payment().put("currency","TRY").put("paymentCurrency","USD").put("paymentAmount",2)
   .put("rateSnapshot",JSONObject("""{"TRY":25}"""))
  val a=LegacyHistoricalSalesProfitReader.extractOriginal(listOf(s),listOf(p),emptyList(),emptyList())
  assertTrue(a.unresolved.isEmpty())
  assertEquals(0,a.events.single().profitUsd.compareTo(BigDecimal("0.8")))
 }
 @Test fun invalidLaterReceiptQuarantinesWholeInvoice() {
  val a=LegacyHistoricalSalesProfitReader.extractOriginal(listOf(raw()),listOf(payment(),payment("p2").put("date","bad")),emptyList(),emptyList())
  assertTrue(a.events.isEmpty());assertEquals(1,a.unresolved.size)
 }
 @Test fun duplicatePaymentsAreNotCounted() {
  val a=LegacyHistoricalSalesProfitReader.extractOriginal(listOf(raw()),listOf(payment(),payment()),emptyList(),emptyList())
  assertTrue(a.events.isEmpty());assertFalse(a.unresolved.isEmpty())
 }
 @Test fun missingCostIsNotZeroButUsdNeedsNoFxSnapshot() {
  val s=raw("store");s.remove("rateSnapshot")
  val valid=LegacyHistoricalSalesProfitReader.extractOriginal(listOf(s),emptyList(),emptyList(),emptyList())
  assertEquals(1,valid.events.size)
  s.getJSONArray("items").getJSONObject(0).remove("cost")
  val invalid=LegacyHistoricalSalesProfitReader.extractOriginal(listOf(s),emptyList(),emptyList(),emptyList())
  assertTrue(invalid.events.isEmpty());assertFalse(invalid.unresolved.isEmpty())
 }
 @Test fun collectionDiscountRecognizesCostAndSkipsNonCashRows() {
  val p=payment(amount=90).put("onlineCollectionDiscount",10)
  val a=LegacyHistoricalSalesProfitReader.extractOriginal(listOf(raw()),listOf(p,payment("ignored").put("nonCash",true)),emptyList(),emptyList())
  assertTrue(a.unresolved.isEmpty())
  assertEquals(0,a.events.single().profitUsd.compareTo(BigDecimal("30")))
 }
}
