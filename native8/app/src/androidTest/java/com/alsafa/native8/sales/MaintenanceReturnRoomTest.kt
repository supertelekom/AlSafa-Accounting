package com.alsafa.native8.sales

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alsafa.native8.maintenance.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.math.BigDecimal

/** Instrumented tests: require Android/Room runtime; not part of standalone JVM validation. */
@RunWith(AndroidJUnit4::class)
class MaintenanceReturnRoomTest {
 private lateinit var db:AppDatabase
 private lateinit var repository:MaintenanceReturnRepository
 @Before fun setup()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<Context>()
  db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
  repository=MaintenanceReturnRepository(db,context)
  val raw=JSONObject("""{"id":"r","invoiceNo":432,"status":"تم التسليم","date":"2026-11-01","deliveredDate":"2026-11-01","currency":"USD","price":100,"laborPrice":60,"tax":0,"taxRate":0,"received":100,"cost":20,"manualCost":0,"financialSnapshotVersion":1,"rateSnapshot":{"USD":1,"TRY":50,"SYP":140,"TRY_SYP":3},"usedParts":[{"partId":"old","name":"قطعة","qty":2,"price":20,"cost":10,"currency":"USD","costSnapshotVerified":true}],"paymentLines":[{"amount":100,"currency":"USD","fxRate":1,"invoiceCredit":100}]}""")
  db.maintenanceDao().insertNewRepair(MaintenanceRepairEntity("r","native8",432,"2026-11-01","12:00","تم التسليم","","","","","","USD","100","100","2026-11-01",1L,raw.toString()))
  for(id in listOf("old","replacement")) db.maintenanceDao().insertNewPart(MaintenancePartEntity(id,"native8",id,"","","5","USD","15","50",1L,"{}"))
  db.legacyPaymentDao().insertCashboxMovement(RoomCashboxMovement("delivery","drawer","USD","100","r",1L,""))
 }
 @After fun close() { db.close() }
 private fun q()=MaintenanceReturnRules.Request("return-1","exchange",0,BigDecimal.ONE,BigDecimal.ZERO,true,"replacement",BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.TEN,"drawer","2026-11-02","12:00","استبدال")
 @Test fun retryDoesNotRepeatStockCashOrProfit()=runBlocking {
  val q=q();val (expected,record)=repository.preview("r",q)
  repository.save("r",q,expected,record.toString());repository.save("r",q,expected,record.toString())
  assertEquals("6",db.maintenanceDao().part("old")!!.stock)
  assertEquals("4",db.maintenanceDao().part("replacement")!!.stock)
  assertEquals(1,db.legacyPaymentDao().cashboxMovements(q.id).size)
  val audit=NativeMaintenanceProfitRepository(db.maintenanceDao(),db.legacyPaymentDao()).read().audit
  assertTrue(audit.unresolved.toString(),audit.unresolved.isEmpty());assertEquals(2,audit.events.size)
 }
 @Test fun duplicateCashIdRollsBackStockAndRepair()=runBlocking {
  val q=q();val (expected,record)=repository.preview("r",q)
  db.legacyPaymentDao().insertCashboxMovement(RoomCashboxMovement("repair-return-cash-"+q.id,"drawer","USD","1","collision",1L,""))
  var rejected=false
  try { repository.save("r",q,expected,record.toString()) } catch(_:Exception) { rejected=true }
  assertTrue(rejected);assertEquals(expected,db.maintenanceDao().repair("r")!!.original_json)
  assertEquals("5",db.maintenanceDao().part("old")!!.stock);assertEquals("5",db.maintenanceDao().part("replacement")!!.stock)
  assertTrue(db.legacyPaymentDao().cashboxMovements(q.id).isEmpty())
 }
 @Test fun changedStockRequiresNewConfirmation()=runBlocking {
  val q=q();val (expected,record)=repository.preview("r",q)
  db.maintenanceDao().changePartStock("replacement","5","4")
  var rejected=false
  try { repository.save("r",q,expected,record.toString()) } catch(_:IllegalArgumentException) { rejected=true }
  assertTrue(rejected);assertEquals(expected,db.maintenanceDao().repair("r")!!.original_json)
  assertEquals("5",db.maintenanceDao().part("old")!!.stock)
 }
 @Test fun collectionRetryClosesDebtOnlyOnce()=runBlocking {
  val q=q();val (expected,record)=repository.preview("r",q);repository.save("r",q,expected,record.toString())
  val (before,payment)=repository.previewCollection("r","payment-1",BigDecimal(20),"drawer","2026-11-03","12:00","")
  repository.saveCollection("r",before,payment.toString());repository.saveCollection("r",before,payment.toString())
  assertEquals(1,db.legacyPaymentDao().cashboxMovements("payment-1").size)
  val raw=JSONObject(db.maintenanceDao().repair("r")!!.original_json)
  assertEquals(0,MaintenanceReturnRules.due(raw).signum())
  val audit=NativeMaintenanceProfitRepository(db.maintenanceDao(),db.legacyPaymentDao()).read().audit
  assertTrue(audit.unresolved.toString(),audit.unresolved.isEmpty());assertEquals(3,audit.events.size)
 }
}
