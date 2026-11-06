package com.alsafa.native8.sales
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class NativeMaintenanceProfitReaderTest {
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
}
