package com.alsafa.native8.sales
import com.alsafa.native8.maintenance.MaintenanceReturnRules as Rules
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class MaintenanceReturnRulesTest {
 private fun fixture()=JSONObject(javaClass.getResourceAsStream("/maintenance-return-v359.json")!!.bufferedReader().use { it.readText() })
 private fun case(index:Int)=fixture().getJSONArray("cases").getJSONObject(index)
 private fun request(c:JSONObject):Rules.Request {
  val f=c.getJSONObject("form")
  fun n(k:String)=f.optString(k,"0").toBigDecimal()
  return Rules.Request(c.getJSONObject("expected").getString("id"),f.getString("kind"),f.optString("repairPartIndex").toIntOrNull() ?: -1,
   n("returnedPartQty"),n("creditAmount"),f.optString("restoreReturnedPart").isNotBlank(),f.optString("replacementPartId"),
   n("replacementPartQty"),n("replacementAmount"),n("extraPaymentNow"),f.getString("vault"),f.getString("date"),f.getString("time"),f.getString("notes"))
 }
 private fun calculate(c:JSONObject,q:Rules.Request=request(c))=Rules.calculate(c.getJSONObject("input"),q,c.optJSONObject("replacement"),c.getJSONObject("rates"))
 private fun decimal(message:String,expected:BigDecimal,actual:BigDecimal) {
  assertTrue("$message: $expected != $actual",(expected-actual).abs()<BigDecimal("0.000000001"))
 }
 private fun rejected(action:()->Unit) { try { action();fail("Expected rejection") } catch(_:IllegalArgumentException) {} }
 @Test fun originalSaveFunctionMatchesAllSettlementAmounts() {
  assertEquals("a57ba7aac9ead8138e6317eb49e84dcd312b7175cb57ca4c60cabf2fb7096126",fixture().getString("sourceSha256"))
  val cases=fixture().getJSONArray("cases");assertEquals(12,cases.length())
  for(i in 0 until cases.length()) {
   val c=cases.getJSONObject(i);val actual=calculate(c);val expected=c.getJSONObject("expected")
   for(key in expected.keySet()) {
    val value=expected.get(key)
    if(value is Number) decimal(c.getString("name")+"/"+key,value.toString().toBigDecimal(),actual.get(key).toString().toBigDecimal())
    else if(value !is JSONObject) assertEquals(c.getString("name")+"/"+key,value.toString(),actual.get(key).toString())
   }
  }
 }
 @Test fun damagedPartRefundDoesNotRestoreItsCost() {
  val r=calculate(case(0));decimal("cost",BigDecimal.ZERO,Rules.n(r,"returnedCost"))
  assertFalse(r.getBoolean("returnedPartRestocked"));decimal("refund",BigDecimal(20),Rules.n(r,"refundAmount"))
 }
 @Test fun partQuantityAndOverReturnAreRejected() {
  val c=case(0);val q=request(c)
  rejected { calculate(c,q.copy(qty=BigDecimal(3))) }
  rejected { calculate(c,q.copy(qty=BigDecimal("0.5"))) }
  rejected { calculate(c,q.copy(partIndex=-1,credit=BigDecimal(101))) }
 }
 @Test fun priorReturnedQuantityCannotBeReturnedTwice() {
  val c=case(10);rejected { calculate(c,request(c).copy(qty=BigDecimal(2))) }
 }
 @Test fun noReplacementStockCannotBeCoveredBySimultaneousRestock() {
  val c=case(3);c.getJSONObject("replacement").put("stock",0)
  rejected { calculate(c) }
 }
 @Test fun pendingRepairOrBackdatedSettlementRejected() {
  val c=case(0);val q=request(c)
  rejected { calculate(c,q.copy(date="2026-10-31")) }
  c.getJSONObject("input").put("status","جاهز للاستلام")
  rejected { calculate(c,q) }
 }
 @Test fun sameOperationIdCannotProduceAnotherReturn() {
  val c=case(0);val r=calculate(c)
  c.getJSONObject("input").put("nativeReturns",JSONArray().put(r))
  rejected { calculate(c) }
 }
 private fun native(c:JSONObject):JSONObject {
  val raw=c.getJSONObject("input")
  raw.put("paymentLines",JSONArray().put(JSONObject().put("amount",raw.get("received")).put("currency",raw.get("currency")).put("fxRate",1).put("invoiceCredit",raw.get("received"))))
  return NativeMaintenanceProfitReader.snapshot(raw,raw.getString("currency"),c.getJSONObject("rates"))
 }
 @Test fun deliveryReturnAndLaterPaymentHaveSeparateDatesAndStableEvents() {
  val c=case(3);val raw=native(c);val before=NativeMaintenanceProfitReader.extract(listOf(raw));assertTrue(before.unresolved.toString(),before.unresolved.isEmpty())
  val r=calculate(c);raw.put("nativeReturns",JSONArray().put(r))
  decimal("remaining",BigDecimal(20),Rules.due(raw))
  val payment=Rules.collection(raw,"collection-1",BigDecimal(20),"drawer","2026-11-03","13:00","",c.getJSONObject("rates"))
  raw.put("nativePayments",JSONArray().put(payment));decimal("debt",BigDecimal.ZERO,Rules.due(raw))
  val json=raw.toString();val audit=NativeMaintenanceProfitReader.extract(listOf(raw))
  assertTrue(audit.unresolved.toString(),audit.unresolved.isEmpty());assertEquals(3,audit.events.size)
  assertEquals(setOf("2026-11-01","2026-11-02","2026-11-03"),audit.events.map { it.recognizedOn.toString() }.toSet())
  assertEquals(3,audit.events.map { it.sourceKey }.toSet().size)
  assertEquals(audit,NativeMaintenanceProfitReader.extract(listOf(raw)));assertEquals(json,raw.toString())
  // The original ordinary receipt rule allocates original repair cost proportionally again.
  decimal("later receipt profit",BigDecimal(16),audit.events.single { it.recognizedOn.toString()=="2026-11-03" }.profitUsd)
 }
 @Test fun collectionCannotExceedDebtOrReuseId() {
  val c=case(3);val raw=native(c);raw.put("nativeReturns",JSONArray().put(calculate(c)))
  rejected { Rules.collection(raw,"p",BigDecimal(21),"drawer","2026-11-03","13:00","",c.getJSONObject("rates")) }
  val p=Rules.collection(raw,"p",BigDecimal(10),"drawer","2026-11-03","13:00","",c.getJSONObject("rates"))
  raw.put("nativePayments",JSONArray().put(p))
  rejected { Rules.collection(raw,"p",BigDecimal(10),"drawer","2026-11-03","13:00","",c.getJSONObject("rates")) }
  rejected { Rules.collection(raw,"p2",BigDecimal(1),"drawer","2026-11-02","13:00","",c.getJSONObject("rates")) }
 }
 @Test fun originalReturnProfitMatchesNativeProjectionIncludingTaxAndFx() {
  for(i in listOf(0,1,2,3,5,7,8,9)) {
   val c=case(i);val raw=native(c);val r=calculate(c);raw.put("nativeReturns",JSONArray().put(r))
   val audit=NativeMaintenanceProfitReader.extract(listOf(raw));assertTrue(audit.unresolved.toString(),audit.unresolved.isEmpty())
   val expected=LegacyMaintenanceProfitReader.extract(listOf(raw),emptyList(),listOf(c.getJSONObject("expected")))
   decimal(c.getString("name"),expected.events.single { it.stream==FinancialSourceReconciliation.Stream.RETURN }.profitUsd,
    audit.events.single { it.stream==FinancialSourceReconciliation.Stream.RETURN }.profitUsd)
  }
 }
}
