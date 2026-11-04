package com.alsafa.native8.maintenance

import androidx.room.withTransaction
import android.content.Context
import com.alsafa.native8.sales.AppDatabase
import com.alsafa.native8.sales.RoomCashboxMovement
import com.alsafa.native8.sales.ExchangeRateSettings
import java.math.RoundingMode
import org.json.JSONObject
import org.json.JSONArray
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Delivery, part stock, and actual collection are committed together. */
class MaintenanceDeliveryRepository(private val db:AppDatabase, context:Context) {
 private val exchangeRates=ExchangeRateSettings(context.applicationContext)
 data class PaymentLine(val amount:String,val currency:String,val cashbox:String,val invoiceCredit:String)
 private fun amount(s:String)=s.toBigDecimalOrNull() ?: error("قيمة مالية غير صحيحة")
 private fun fmt(n:BigDecimal)=n.stripTrailingZeros().toPlainString()
 suspend fun deliver(id:String,collection:String,cashbox:String):Unit =
  deliver(id,listOf(PaymentLine(collection,"USD",cashbox,collection)),true)
 suspend fun deliver(id:String,payments:List<PaymentLine>,legacySingleCurrency:Boolean=false):Unit=db.withTransaction {
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
  require(payments.size==1) { "اختر مبلغاً وصندوقاً وعملة واحدة" }
  val prepared=payments.map { payment ->
   val currency=if(legacySingleCurrency) repair.currency else payment.currency
   require(currency in setOf("USD","TRY","SYP")) { "عملة غير مدعومة" }
   require(payment.cashbox in setOf("drawer","external","ChamCash","bank")) { "الصندوق غير معروف" }
   val actual=amount(payment.amount)
   val rate=exchangeRates.rate(currency,repair.currency)
   val credit=actual.multiply(rate).setScale(2,RoundingMode.HALF_UP)
   require(actual>BigDecimal.ZERO && credit>BigDecimal.ZERO) { "الدفعات يجب أن تكون موجبة" }

   Triple(payment,actual,credit)
  }
  val received=prepared.fold(BigDecimal.ZERO) { sum,line -> sum+line.third }
  require(received.compareTo(total)==0) { "المبلغ المقبوض لا يساوي المستحق بعد التحويل بسعر الصرف المحفوظ" }
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
  raw.put("paymentMethod",if(prepared.size==1) prepared.first().first.cashbox else "قبض مختلط")
  raw.put("paymentLines",JSONArray().apply {
   prepared.forEach { (payment,actual,credit) ->
    put(JSONObject().put("currency",if(legacySingleCurrency) repair.currency else payment.currency)
     .put("amount",fmt(actual)).put("invoiceCredit",fmt(credit)).put("cashbox",payment.cashbox)
     .put("fxRate",fmt(credit.divide(actual,12,RoundingMode.HALF_UP))))
   }
  })
  check(dao.finalizeDelivery(id,"تم التسليم",fmt(total),fmt(received),day,raw.toString(),repair.original_json)==1) { "تغيرت الصيانة أثناء التسليم" }
  prepared.forEach { (payment,actual,credit) ->
   db.legacyPaymentDao().insertCashboxMovement(RoomCashboxMovement(
    "repair-payment-"+UUID.randomUUID(),payment.cashbox,
    if(legacySingleCurrency) repair.currency else payment.currency,
    fmt(actual),id,now,"تحصيل تسليم صيانة رقم "+repair.invoice_number+" — محسوب "+fmt(credit)+" "+repair.currency
   ))
  }
 }
}
