package com.alsafa.native8.maintenance
import android.content.Context
import androidx.room.withTransaction
import com.alsafa.native8.sales.*
import com.alsafa.native8.data.migration.LegacyStagingDatabase
import com.alsafa.native8.data.migration.LegacyReadRepository
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

class QuickRepairRepository(private val context:Context) {
 fun settings():JSONObject {
  val staging=LegacyStagingDatabase(context)
  try { val reader=LegacyReadRepository(staging);val run=reader.latestRunId() ?: return JSONObject()
   return JSONObject(reader.objectSection(run,"settings") ?: "{}")
  } finally { staging.close() }
 }
 suspend fun save(id:String,buttonId:String,base:String,finalAmount:String,currency:String,method:String,work:String,deliver:Boolean,customer:String,phone:String):Long {
  val settings=settings();val button=QuickRepairRules.buttons(settings).firstOrNull { it.getString("id")==buttonId } ?: error("اختر خدمة صيانة موجودة")
  val deferred= !deliver
  fun parseAmount(value:String):java.math.BigDecimal {
   if(deferred && value.isBlank()) return java.math.BigDecimal.ZERO
   return runCatching { QuickRepairRules.amount(value) }.getOrElse { error("أدخل مبلغاً صحيحاً") }
  }
  val baseValue=parseAmount(base);val finalValue=parseAmount(finalAmount)
  require(baseValue.signum()>=0 && finalValue.signum()>=0) { "لا يمكن إدخال مبلغ سالب" }
  require(deferred || (baseValue.signum()>0 && finalValue.signum()>0)) { "اكتب مبلغ الصيانة أولاً" }
  require(deferred || button.optString("priceMode")!="manual" || work.isNotBlank()) { "اكتب العمل الذي تم" }
  require(deliver || customer.isNotBlank()) { "اكتب اسم العميل لأن الساعة ستبقى لدينا" }
  val vault=when(method) { "نقدي" -> "drawer";"شام كاش" -> "shamcash";else -> error("طريقة دفع غير مدعومة") }
  require(vault!="shamcash" || currency!="TRY") { "شام كاش لا يدعم الليرة التركية" }
  val db=AppDatabase.get(context);val rates=ExchangeRateSettings(context)
  return db.withTransaction {
   val dao=db.maintenanceDao();dao.repair(id)?.let { return@withTransaction it.invoice_number }
   val linked=button.optString("partId").takeIf { it.isNotBlank() }?.let { dao.part(it) ?: error("قطعة الصيانة المرتبطة غير موجودة") }
   val part=linked?.let { JSONObject().put("id",it.id).put("name",it.name).put("stock",it.stock).put("currency",it.currency).put("cost",it.cost) }
   val raw=QuickRepairRules.financial(settings,button,part,finalValue,currency,rates::rate,allowZero=deferred)
   val number=maxOf(dao.maxRepairInvoice(),db.salesPostingDao().maxNewNumber(),db.legacySaleDao().maxLegacyInvoiceNumber())+1
   val day=LocalDate.now().toString();val time=LocalTime.now().toString().take(5);val now=System.currentTimeMillis()
   val status=if(deliver) "جاهز للاستلام" else if(finalValue.signum()==0 && work.isBlank()) "قيد الصيانة" else "بانتظار التسليم";val name=button.getString("label")
   raw.put("id",id).put("invoiceNo",number).put("date",day).put("time",time).put("status",status)
    .put("customer",if(deliver) "" else customer).put("phone",if(deliver) "" else phone).put("isSimplifiedQuick",true)
    .put("quickServiceName",name).put("quickButtonId",buttonId).put("watch",name).put("model","").put("problem","خدمة صيانة سريعة")
    .put("diagnosis",work).put("workDone",work).put("watchPhoto","").put("received",0).put("paymentMethod",method)
    .put("paymentStatus","عند الاستلام").put("notes","صيانة من الوضع المبسط").put("createdAt",now)
   dao.insertNewRepair(MaintenanceRepairEntity(id,"native8",number,day,time,status,raw.getString("customer"),raw.getString("phone"),name,"","خدمة صيانة سريعة",currency,raw.getString("price"),"0","",now,raw.toString()))
   if(deliver) MaintenanceDeliveryRepository(db,context).deliver(id,listOf(MaintenanceDeliveryRepository.PaymentLine(raw.getString("price"),currency,vault,raw.getString("price"))))
   number
  }
 }
}
