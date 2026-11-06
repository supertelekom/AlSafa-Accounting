package com.alsafa.native8.maintenance

import android.content.Context
import androidx.room.withTransaction
import com.alsafa.native8.sales.*
import com.alsafa.native8.data.migration.LegacyStagingDatabase
import com.alsafa.native8.data.migration.LegacyReadRepository
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** New delivery of a restored repair; original source and old cash remain untouched. */
class ArchivedRepairDeliveryRepository(private val db:AppDatabase,private val context:Context) {
 private fun due(row:MaintenanceRepairEntity):BigDecimal {
  val staging=LegacyStagingDatabase(context)
  try {
   val run=staging.readableDatabase.rawQuery("SELECT id FROM import_runs WHERE source_sha256=?",arrayOf(row.source_sha256)).use { require(it.moveToFirst()){ "مصدر الفاتورة غير موجود" };it.getLong(0) }
   val source=LegacyReadRepository(staging)
   return ArchivedRepairDeliveryRules.due(JSONObject(row.original_json),source.financialRows(run,"payments"),source.financialRows(run,"returns"))
  } finally {staging.close()}
 }
 suspend fun balance(id:String):BigDecimal = due(requireNotNull(db.maintenanceDao().repair(id)))
 suspend fun deliver(id:String,expected:String,expectedDue:BigDecimal,actual:BigDecimal,currency:String,vault:String)=db.withTransaction {
  val dao=db.maintenanceDao();val row=dao.repair(id) ?: error("الفاتورة غير موجودة")
  require(row.source_sha256!="native8" && ArchivedRepairDeliveryRules.ready(row.status)){ "تم التسليم مسبقاً أو تغيرت الحالة" }
  require(row.original_json==expected){ "تغيرت الفاتورة؛ أعد فتح التسليم" }
  val raw=JSONObject(row.original_json)
  require(raw.getString("id")==id && raw.getString("currency")==row.currency){ "بيانات الفاتورة غير متطابقة" }
  require(!raw.has("nativeArchiveDelivery")){ "تم التسليم مسبقاً" }
  val balance=due(row)
  require(balance.compareTo(expectedDue)==0){ "تغير المتبقي؛ أعد فتح التسليم" }
  require(actual>=BigDecimal.ZERO && if(balance.signum()>0) actual.signum()>0 else actual.signum()==0){ "أدخل المقبوض الفعلي؛ الفاتورة المسددة لا تتطلب قبضاً جديداً" }
  require(currency in setOf("USD","TRY","SYP"))
  val cashbox=UnifiedCashboxBalance.canonical(vault) ?: error("الصندوق غير معروف")
  require(cashbox!="shamcash" || currency!="TRY"){ "شام كاش لا يدعم التركي" }
  val rates=ExchangeRateSettings(context)
  val snapshot=JSONObject().put("USD",1).put("TRY",rates.rate("USD","TRY").toPlainString()).put("SYP",rates.rate("USD","SYP").toPlainString())
  val now=System.currentTimeMillis();val zone=ZoneId.of("Asia/Damascus")
  val day=LocalDate.now(zone).toString();val time=LocalTime.now(zone).toString()
  val key="archive-repair-delivery:$id"
  val delivery=JSONObject().put("original",row.original_json).put("due",balance.toPlainString()).put("date",day)
  if(balance.signum()>0) delivery.put("payment",JSONObject().put("id",key).put("referenceType","repair").put("referenceId",id)
   .put("amount",balance.toPlainString()).put("currency",row.currency).put("paymentCurrency",currency).put("paymentAmount",actual.toPlainString())
   .put("vault",cashbox).put("date",day).put("time",time).put("rateSnapshot",snapshot).put("paymentType","repair-delivery-actual"))
  raw.put("nativeArchiveDelivery",delivery)
  val audit=ArchivedRepairDeliveryRules.audit(raw)
  require(audit.unresolved.isEmpty()){audit.unresolved.joinToString("\n")}
  // Original applyRepairPartsStock: skip stock already applied in the backup.
  if(!raw.optBoolean("partsStockApplied")) {
   val lines=raw.optJSONArray("usedParts")
   val quantities=mutableMapOf<String,BigDecimal>()
   for(i in 0 until (lines?.length() ?: 0)) {
    val line=lines!!.getJSONObject(i);val qty=line.get("qty").toString().toBigDecimal()
    require(qty>BigDecimal.ZERO);val partId=line.getString("partId")
    quantities[partId]=(quantities[partId] ?: BigDecimal.ZERO)+qty
   }
   for((partId,qty) in quantities) {
    val part=dao.part(partId) ?: error("قطعة الصيانة غير موجودة")
    val after=part.stock.toBigDecimal()-qty
    require(after>=BigDecimal.ZERO){ "المخزون غير كافٍ: ${part.name}" }
    check(dao.changePartStock(partId,part.stock,after.toPlainString())==1)
   }
   raw.put("partsStockApplied",true).put("partsStockAppliedAt",now)
  }
  raw.put("status","تم التسليم").put("deliveredDate",day).put("deliveredTime",time).put("paymentStatus","مدفوع بالكامل")
  if(balance.signum()>0) raw.put("deliveryPaymentCurrency",currency).put("deliveryPaymentAmount",actual.toPlainString())
  check(dao.finalizeArchivedDelivery(id,row.original_json,raw.toString(),day)==1){ "تغيرت الفاتورة أثناء الحفظ" }
  if(balance.signum()>0) db.legacyPaymentDao().insertCashboxMovement(RoomCashboxMovement(key,cashbox,currency,actual.toPlainString(),key,now,"تحصيل تسليم صيانة رقم ${row.invoice_number}"))
 }
}
