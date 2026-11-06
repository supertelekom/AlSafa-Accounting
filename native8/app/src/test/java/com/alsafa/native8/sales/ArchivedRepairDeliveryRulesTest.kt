package com.alsafa.native8.sales
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import java.math.BigDecimal

class ArchivedRepairDeliveryRulesTest {
 private fun original()=JSONObject("""{"id":"r1","date":"2026-09-22","currency":"USD","price":100,"received":20,"cost":40,"tax":0,"rateSnapshot":{"USD":1}}""")
 private fun payment()=JSONObject("""{"id":"new-delivery","referenceType":"repair","referenceId":"r1","amount":50,"currency":"USD","paymentCurrency":"TRY","paymentAmount":2500,"date":"2026-11-06","rateSnapshot":{"USD":1,"TRY":50}}""")
 @Test fun subtractsInitialAndSeparatePaymentsAndReturnCredit() {
  val old=payment().put("amount",30)
  val returned=JSONObject("""{"referenceType":"repair","referenceId":"r1","creditAmount":10,"refundAmount":5}""")
  assertEquals(0,BigDecimal(45).compareTo(ArchivedRepairDeliveryRules.due(original(),listOf(old),listOf(returned))))
 }
 @Test fun onlyNewReceiptProfitAppearsOnDeliveryDay() {
  val raw=original().put("nativeArchiveDelivery",JSONObject().put("original",original().toString()).put("payment",payment()))
  val audit=ArchivedRepairDeliveryRules.audit(raw)
  assertTrue(audit.unresolved.toString(),audit.unresolved.isEmpty())
  val event=audit.events.single()
  assertEquals("2026-11-06",event.recognizedOn.toString())
  assertEquals(0,BigDecimal(30).compareTo(event.profitUsd))
  assertEquals("native:repair:r1:delivery",event.sourceKey)
  assertEquals(20,raw.getInt("received"))
 }
 @Test fun alreadyPaidDeliveryDoesNotCreateProfitAgain() {
  val raw=original().put("nativeArchiveDelivery",JSONObject().put("original",original().put("received",100).toString()))
  assertTrue(ArchivedRepairDeliveryRules.audit(raw).events.isEmpty())
 }
 @Test fun newReceiptUsesCurrentSnapshotAndPreservesOriginalCost() {
  val original=original().put("currency","TRY").put("price",250).put("received",0).put("cost",100)
  val p=payment().put("currency","TRY").put("amount",250).put("paymentCurrency","USD").put("paymentAmount",5)
  val raw=JSONObject(original.toString()).put("nativeArchiveDelivery",JSONObject().put("original",original.toString()).put("payment",p))
  val audit=ArchivedRepairDeliveryRules.audit(raw)
  assertTrue(audit.unresolved.toString(),audit.unresolved.isEmpty())
  assertEquals(0,BigDecimal(3).compareTo(audit.events.single().profitUsd))
 }
 @Test fun deliveredCannotBeSelectedAgain() {
  assertTrue(ArchivedRepairDeliveryRules.ready("جاهز للاستلام"))
  assertTrue(ArchivedRepairDeliveryRules.ready("بانتظار التسليم"))
  assertFalse(ArchivedRepairDeliveryRules.ready("تم التسليم"))
 }
 @Test fun repositoryIncludesNewProfitOnceAndValidatesCash() = kotlinx.coroutines.runBlocking {
  val p=payment().put("vault","drawer")
  val raw=original().put("status","تم التسليم").put("nativeArchiveDelivery",JSONObject().put("original",original().toString()).put("payment",p).put("date","2026-11-06"))
  val row=com.alsafa.native8.maintenance.MaintenanceRepairEntity("r1","backup-sha",1,"2026-09-22","12:00","تم التسليم","","","","","","USD","100","20","2026-11-06",0,raw.toString())
  val dao=java.lang.reflect.Proxy.newProxyInstance(com.alsafa.native8.maintenance.MaintenanceDao::class.java.classLoader,arrayOf(com.alsafa.native8.maintenance.MaintenanceDao::class.java)) { _,method,_ ->
   when(method.name){"nativeFinancialRecords"->emptyList<com.alsafa.native8.maintenance.MaintenanceRepairEntity>();"allDelivered"->listOf(row);else->error(method.name)}
  } as com.alsafa.native8.maintenance.MaintenanceDao
  var posted=true
  val cash=java.lang.reflect.Proxy.newProxyInstance(LegacyPaymentDao::class.java.classLoader,arrayOf(LegacyPaymentDao::class.java)) { _,method,_ ->
   check(method.name=="cashboxMovements")
   if(posted) listOf(RoomCashboxMovement("new-delivery","drawer","TRY","2500","new-delivery",0,"")) else emptyList<RoomCashboxMovement>()
  } as LegacyPaymentDao
  val repo=NativeMaintenanceProfitRepository(dao,cash)
  repeat(2) {
   val result=repo.read("2026-11-06")
   assertTrue(result.audit.unresolved.toString(),result.audit.unresolved.isEmpty())
   assertEquals(0,BigDecimal(30).compareTo(result.audit.events.single().profitUsd))
   assertTrue(result.ids.isEmpty()) // original ID remains historical, avoiding false overlap
  }
  assertTrue(repo.read("2026-10-31").audit.events.isEmpty())
  posted=false
  assertTrue(repo.read().audit.events.isEmpty())
  assertTrue(repo.read().audit.unresolved.isNotEmpty())
 }

 @Test fun threeOriginalReadyRepairsUseTheirOriginalAmountsAndCosts() {
  val fixture=JSONObject(javaClass.getResourceAsStream("/historical-finance-v359.json")!!.bufferedReader().use { it.readText() })
  fun rows(key:String)=fixture.getJSONArray(key).let { a->(0 until a.length()).map { a.getJSONObject(it) } }
  val expected=mapOf("repair-1791793564324-u0wp4f" to "2.7018","repair-1791281445385-r9213n" to "4","repair-1790850645182-f2rf2p" to "40")
  for((id,profit) in expected) {
   val record=rows("repairs").single {it.getString("id")==id}
   val due=ArchivedRepairDeliveryRules.due(record,rows("payments"),rows("returns"))
   val p=payment().put("referenceId",id).put("currency",record.getString("currency")).put("amount",due.toPlainString())
   val raw=JSONObject(record.toString()).put("nativeArchiveDelivery",JSONObject().put("original",record.toString()).put("payment",p))
   val audit=ArchivedRepairDeliveryRules.audit(raw)
   assertTrue(audit.unresolved.toString(),audit.unresolved.isEmpty())
   assertEquals(0,profit.toBigDecimal().compareTo(audit.events.single().profitUsd))
  }
 }

}
