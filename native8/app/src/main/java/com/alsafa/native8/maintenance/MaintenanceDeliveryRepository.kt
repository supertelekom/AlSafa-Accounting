package com.alsafa.native8.maintenance

import androidx.room.withTransaction
import com.alsafa.native8.sales.AppDatabase
import com.alsafa.native8.sales.RoomCashboxMovement
import org.json.JSONObject
import org.json.JSONArray
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Delivery, part stock, and actual collection are committed together. */
class MaintenanceDeliveryRepository(private val db:AppDatabase) {
 private fun amount(s:String)=s.toBigDecimalOrNull() ?: error("قيمة مالية غير صحيحة")
 private fun fmt(n:BigDecimal)=n.stripTrailingZeros().toPlainString()
 suspend fun deliver(id:String,collection:String,cashbox:String):Unit=db.withTransaction {
  val dao=db.maintenanceDao()
  val repair=dao.repair(id) ?: error("فاتورة الصيانة غير موجودة")
  require(repair.source_sha256=="native8" && repair.status!="تم التسليم") { "تم تسليم الصيانة مسبقاً أو السجل تاريخي" }
  require(repair.status=="جاهز للاستلام") { "يجب تجهيز الصيانة قبل تسليمها" }
  val raw=JSONObject(repair.original_json)
  require(!raw.optBoolean("partsStockApplied",false)) { "مخزون القطع خُصم مسبقاً" }
  val lines=raw.optJSONArray("usedParts") ?: JSONArray()
  val labor=amount(raw.optString("laborPrice",raw.optString("price","0")))
  val partsTotal=(0 until lines.length()).fold(BigDecimal.ZERO) { sum,i ->
   val line=lines.getJSONObject(i)
   sum+amount(line.optString("qty","0"))*amount(line.optString("price","0"))
  }
  val total=labor+partsTotal
  require(total>=BigDecimal.ZERO) { "الإجمالي غير صحيح" }
  val received=amount(collection)
  require(received>=BigDecimal.ZERO && received<=total) { "المقبوض يجب أن يكون بين صفر والإجمالي" }
  require(cashbox in setOf("drawer","external","ChamCash","bank")) { "الصندوق غير معروف" }
  // Validate all part quantities before any mutation.
  val updates=mutableListOf<Triple<MaintenancePartEntity,String,String>>()
  val seen=mutableSetOf<String>()
  for(i in 0 until lines.length()) {
   val line=lines.getJSONObject(i)
   val partId=line.getString("partId")
   require(seen.add(partId)) { "قطعة مكررة في الصيانة" }
   val part=dao.part(partId) ?: error("قطعة الصيانة غير موجودة")
   val qty=amount(line.getString("qty"))
   val before=amount(part.stock)
   require(qty>BigDecimal.ZERO && qty.stripTrailingZeros().scale()<=0 && before>=qty) { "المخزون غير كافٍ: ${part.name}" }
   updates.add(Triple(part,part.stock,fmt(before-qty)))
  }
  updates.forEach { (part,before,after) ->
   check(dao.changePartStock(part.id,before,after)==1) { "تغير مخزون القطعة: ${part.name}" }
  }
  val now=System.currentTimeMillis()
  val day=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date(now))
  raw.put("status","تم التسليم")
  raw.put("deliveredDate",day)
  raw.put("deliveredTime",SimpleDateFormat("HH:mm",Locale.US).format(Date(now)))
  raw.put("partsTotal",fmt(partsTotal))
  raw.put("price",fmt(total))
  raw.put("partsStockApplied",true)
  raw.put("partsStockAppliedAt",now)
  raw.put("received",fmt(received))
  raw.put("paymentStatus",if(received==total) "مدفوع بالكامل" else if(received>BigDecimal.ZERO) "دفعة جزئية" else "غير مدفوع")
  raw.put("paymentMethod",cashbox)
  check(dao.finalizeDelivery(id,"تم التسليم",fmt(total),fmt(received),day,raw.toString(),repair.original_json)==1) { "تغيرت الصيانة أثناء التسليم" }
  if(received>BigDecimal.ZERO) {
   db.legacyPaymentDao().insertCashboxMovement(RoomCashboxMovement(
    "repair-payment-"+UUID.randomUUID(),cashbox,repair.currency,fmt(received),id,now,"تحصيل تسليم صيانة رقم ${repair.invoice_number}"
   ))
  }
 }
}
