package com.alsafa.native8.sales
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class NativeMaintenanceProfitReaderTest {
 @Test fun labourOnlyDeliveryRecognizesFullAmountWithoutPartsOrNotes() {
  val raw=JSONObject("""{"id":"labour-only","status":"تم التسليم","date":"2026-10-01","deliveredDate":"2026-11-06","currency":"USD","laborPrice":10,"price":10,"received":10,"usedParts":[],"paymentLines":[{"amount":10,"currency":"USD","fxRate":1,"invoiceCredit":10}]}""")
  val saved=NativeMaintenanceProfitReader.snapshot(raw,"USD",JSONObject("""{"USD":1}"""))
  assertEquals(0,saved.getString("cost").toBigDecimal().signum())
  val audit=NativeMaintenanceProfitReader.extract(listOf(saved))
  assertTrue(audit.unresolved.toString(),audit.unresolved.isEmpty())
  assertEquals("2026-11-06",audit.events.single().recognizedOn.toString())
  assertEquals(0,BigDecimal.TEN.compareTo(audit.events.single().profitUsd))
  assertEquals(audit,NativeMaintenanceProfitReader.extract(listOf(saved)))
 }
 @Test fun labourOnlyForeignCurrencyUsesSavedRate() {
  val raw=JSONObject("""{"id":"labour-try","status":"تم التسليم","deliveredDate":"2026-11-06","currency":"TRY","laborPrice":500,"price":500,"received":500,"usedParts":[],"paymentLines":[{"amount":500,"currency":"TRY","fxRate":1,"invoiceCredit":500}]}""")
  val saved=NativeMaintenanceProfitReader.snapshot(raw,"TRY",JSONObject("""{"USD":1,"TRY":50}"""))
  val audit=NativeMaintenanceProfitReader.extract(listOf(saved))
  assertTrue(audit.unresolved.toString(),audit.unresolved.isEmpty())
  assertEquals(0,BigDecimal.TEN.compareTo(audit.events.single().profitUsd))
 }
 private fun raw()=JSONObject("""{"id":"r1","status":"تم التسليم","date":"2026-10-01","deliveredDate":"2026-11-01","currency":"USD","price":30,"laborPrice":20,"tax":0,"taxRate":0,"received":30,"cost":0,"manualCost":2,"usedParts":[{"qty":1,"price":10,"cost":5,"currency":"USD","costSnapshotVerified":true}],"paymentLines":[{"amount":1500,"currency":"TRY","fxRate":0.02,"invoiceCredit":30}]}""")
 private fun verified()=NativeMaintenanceProfitReader.snapshot(raw(),"USD",JSONObject("""{"USD":1,"TRY":50,"SYP":140}"""))
 @Test fun partsAndManualCostDeductedOnceAtDeliveryDate() {
  val a=NativeMaintenanceProfitReader.extract(listOf(verified()))
  assertEquals(emptyList<String>(),a.unresolved)
  assertEquals("2026-11-01",a.events.single().recognizedOn.toString())
  assertEquals(0,a.events.single().profitUsd.compareTo(BigDecimal("23")))
 }
 @Test fun undeliveredRepairDoesNotEarnProfit() {
  val r=raw().put("status","جاهز للاستلام").put("received",0)
  assertTrue(NativeMaintenanceProfitReader.extract(listOf(r)).events.isEmpty())
 }
 @Test fun oldSavedCostUsesOriginalReaderWithoutNewFlags() {
  val old=raw().put("cost",7);old.remove("paymentLines")
  val a=NativeMaintenanceProfitReader.extract(listOf(old))
  assertTrue(a.unresolved.toString(),a.unresolved.isEmpty());assertEquals(0,a.events.single().profitUsd.compareTo(BigDecimal("23")))
 }
 @Test fun foreignInvoiceRequiresHistoricalSnapshot() {
  val r=verified().put("currency","TRY");r.remove("rateSnapshot")
  assertFalse(NativeMaintenanceProfitReader.extract(listOf(r)).unresolved.isEmpty())
 }
 @Test fun repeatedReadDoesNotCreateDifferentEventOrMutateInput() {
  val r=verified();val before=r.toString()
  val first=NativeMaintenanceProfitReader.extract(listOf(r))
  assertEquals(first,NativeMaintenanceProfitReader.extract(listOf(r)))
  assertEquals(before,r.toString())
  assertTrue(NativeMaintenanceProfitReader.extract(listOf(r,r)).events.isEmpty())
 }
 @Test fun changedCostOrMissingReceiptCannotPublishProfit() {
  val r=verified().put("cost",0)
  assertTrue(NativeMaintenanceProfitReader.extract(listOf(r)).events.isEmpty())
  val missing=verified();missing.remove("paymentLines")
  assertTrue(NativeMaintenanceProfitReader.extract(listOf(missing)).events.isEmpty())
 }
 @Test fun taxIsExcludedOnceFromDeliveryRevenue() {
  val r=verified().put("taxRate",10).put("tax",3).put("price",33).put("received",33)
  r.getJSONArray("paymentLines").getJSONObject(0).put("invoiceCredit",33).put("amount",1650)
  val a=NativeMaintenanceProfitReader.extract(listOf(r))
  assertTrue(a.unresolved.isEmpty())
  assertEquals(0,a.events.single().profitUsd.compareTo(BigDecimal("23")))
 }
 @Test fun zeroValueDeliveryHasNoReceiptOrProfitEvent() {
  val raw=JSONObject("""{"id":"free","status":"تم التسليم","date":"2026-11-01","deliveredDate":"2026-11-06","currency":"USD","laborPrice":0,"price":0,"received":0,"tax":0,"taxRate":0,"usedParts":[],"paymentLines":[]}""")
  val saved=NativeMaintenanceProfitReader.snapshot(raw,"USD",JSONObject().put("USD",1))
  val result=NativeMaintenanceProfitReader.extract(listOf(saved))
  assertTrue(result.unresolved.toString(),result.unresolved.isEmpty())
  assertTrue(result.events.isEmpty())
 }
 @Test fun positiveInvoiceCannotUseEmptyReceiptEvidence() {
  val saved=verified().put("paymentLines",org.json.JSONArray()).put("received",0)
  assertTrue(NativeMaintenanceProfitReader.extract(listOf(saved)).unresolved.isNotEmpty())
 }

}
