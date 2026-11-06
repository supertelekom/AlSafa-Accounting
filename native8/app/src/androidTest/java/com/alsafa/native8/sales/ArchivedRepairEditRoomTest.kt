package com.alsafa.native8.sales

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alsafa.native8.maintenance.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class ArchivedRepairEditRoomTest {
 @Test fun archiveDraftRetainsPartCostAndDoesNotPostStockOrCash()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build()
  try {
   val raw=JSONObject("""{"id":"r","invoiceNo":411,"currency":"USD","status":"قيد الصيانة","laborPrice":10,"price":15,"received":0,"cost":2,"partsStockApplied":false,"usedParts":[{"partId":"p","qty":1,"price":5,"cost":2}]}""")
   val dao=db.maintenanceDao()
   dao.insertNewRepair(MaintenanceRepairEntity("r","archive",411,"2026-10-01","12:00","قيد الصيانة","عميل","","ساعة","","عطل","USD","15","0","",1L,raw.toString()))
   dao.insertNewPart(MaintenancePartEntity("p","archive","قطعة","","","8","USD","99","5",1L,"{}"))
   MaintenancePartsRepository(db).saveDraft("r",listOf(MaintenancePartsRepository.SelectedPart("p","2","5")))
   MaintenancePartsRepository(db).refreshDraftTotal("r")
   val edited=dao.repair("r")!!;val saved=JSONObject(edited.original_json)
   assertEquals("archive",edited.source_sha256)
   assertEquals(0,edited.price.toBigDecimal().compareTo(20.toBigDecimal()))
   assertEquals(0,saved.optString("cost").toBigDecimal().compareTo(4.toBigDecimal()))
   assertEquals("8",dao.part("p")!!.stock)
   assertEquals("0",edited.received)
   assertTrue(db.legacyPaymentDao().cashboxMovements("r").isEmpty())
  } finally {db.close()}
 }
}
