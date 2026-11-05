package com.alsafa.native8.maintenance

import android.content.Context
import androidx.room.withTransaction
import com.alsafa.native8.sales.*
import org.json.JSONObject
import org.json.JSONArray
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Append-only native repair settlements; stock, cash and JSON commit or roll back together. */
class MaintenanceReturnRepository(private val db:AppDatabase,context:Context) {
 private val context=context.applicationContext
 private val exchange=ExchangeRateSettings(context)
 private fun rates()=JSONObject().put("USD",1).put("TRY",exchange.rate("USD","TRY").toPlainString())
  .put("SYP",exchange.rate("USD","SYP").toPlainString()).put("TRY_SYP",exchange.rate("TRY","SYP").toPlainString())
 private fun part(p:MaintenancePartEntity)=JSONObject().put("id",p.id).put("name",p.name).put("stock",p.stock)
  .put("salePrice",p.sale_price).put("cost",p.cost).put("currency",p.currency)
 private fun vault(v:String,unit:String):String {
  val key=UnifiedCashboxBalance.canonical(v) ?: error("خزنة غير معروفة")
  require(key!="shamcash" || unit!="TRY") { "شام كاش لا يدعم الليرة التركية" };return key
 }
 private suspend fun stockPlan(record:JSONObject):JSONArray {
  val dao=db.maintenanceDao();val deltas=linkedMapOf<String,BigDecimal>()
  if(record.getBoolean("returnedPartRestocked")) deltas[record.getString("returnedPartId")]=MaintenanceReturnRules.n(record,"returnedPartQty")
  val replacement=record.getString("replacementPartId")
  if(replacement.isNotBlank()) deltas[replacement]=(deltas[replacement] ?: BigDecimal.ZERO)-MaintenanceReturnRules.n(record,"replacementPartQty")
  val plan=JSONArray()
  for((id,delta) in deltas) {
   val part=dao.part(id) ?: error("القطعة غير موجودة في المخزون")
   val after=part.stock.toBigDecimal()+delta;require(after>=BigDecimal.ZERO)
   plan.put(JSONObject().put("partId",id).put("before",part.stock).put("after",after.toPlainString()))
  }
  return plan
 }
 suspend fun preview(id:String,q:MaintenanceReturnRules.Request):Pair<String,JSONObject> = db.withTransaction {
  val repair=db.maintenanceDao().repair(id) ?: error("الصيانة غير موجودة")
  require(repair.source_sha256=="native8") { "السجل التاريخي للقراءة فقط" }
  val raw=JSONObject(repair.original_json)
  require(NativeMaintenanceProfitReader.extract(listOf(raw)).unresolved.isEmpty()) { "أدلة تكلفة/قبض الصيانة تحتاج مراجعة" }
  val replacement=if(q.replacementId.isBlank()) null else db.maintenanceDao().part(q.replacementId)?.let(::part)
  val canonical=q.copy(vault=vault(q.vault,repair.currency))
  val record=MaintenanceReturnRules.calculate(raw,canonical,replacement,rates())
  record.put("inventoryMovements",stockPlan(record))
  repair.original_json to record
 }
 suspend fun save(id:String,q:MaintenanceReturnRules.Request,expected:String,approved:String):JSONObject=db.withTransaction {
  val dao=db.maintenanceDao();val repair=dao.repair(id) ?: error("الصيانة غير موجودة")
  require(repair.source_sha256=="native8") { "السجل التاريخي للقراءة فقط" }
  val raw=JSONObject(repair.original_json)
  val prior=MaintenanceReturnRules.rows(raw,"nativeReturns").firstOrNull { it.optString("id")==q.id }
  if(prior!=null) {
   require(prior.toString()==approved) { "معرف العملية مستخدم لطلب مختلف" };return@withTransaction prior
  }
  require(repair.original_json==expected) { "تغيرت الصيانة؛ أعد معاينة العملية" }
  val replacement=if(q.replacementId.isBlank()) null else dao.part(q.replacementId)
  val record=MaintenanceReturnRules.calculate(raw,q.copy(vault=vault(q.vault,repair.currency)),replacement?.let(::part),rates())
  record.put("inventoryMovements",stockPlan(record))
  require(record.toString()==approved) { "تغير المخزون أو التكلفة أو الصرف؛ أعد المعاينة والتأكيد" }
  val refund=MaintenanceReturnRules.n(record,"refundAmount")
  val box=record.getString("vault")
  if(refund>BigDecimal.ZERO) {
   val balance=UnifiedCashboxBalance(context,db.supplierPurchaseDao()).calculate(box,repair.currency)
   require(balance.verified && balance.total>=refund) { "رصيد الخزنة غير كافٍ أو يحتاج مراجعة" }
  }
  // Immutable before/after evidence is stored in the same settlement as cash and cost.
  val plan=record.getJSONArray("inventoryMovements")
  for(i in 0 until plan.length()) {
   val movement=plan.getJSONObject(i)
   check(dao.changePartStock(movement.getString("partId"),movement.getString("before"),movement.getString("after"))==1) { "تغير مخزون القطعة" }
  }
  val entries=raw.optJSONArray("nativeReturns") ?: JSONArray();entries.put(record);raw.put("nativeReturns",entries)
  val remaining=MaintenanceReturnRules.n(record,"remainingAfter")
  raw.put("paymentStatus",if(MaintenanceReturnRules.effective(raw).signum()==0) "مسوّاة/مسترجعة" else if(remaining<BigDecimal("0.01")) "مدفوع بالكامل" else "دفعة جزئية")
  check(dao.appendSettlement(id,expected,raw.toString())==1) { "تغيرت الصيانة أثناء الحفظ" }
  val effect=MaintenanceReturnRules.n(record,"cashEffect")
  if(effect.signum()!=0) {
   val at=LocalDate.parse(q.date).atTime(LocalTime.parse(q.time)).atZone(ZoneId.of("Asia/Damascus")).toInstant().toEpochMilli()
   db.legacyPaymentDao().insertCashboxMovement(RoomCashboxMovement("repair-return-cash-"+q.id,box,repair.currency,
    effect.toPlainString(),q.id,at,"مرتجع/استبدال صيانة رقم "+repair.invoice_number))
  }
  record
 }
 suspend fun previewCollection(repairId:String,paymentId:String,amount:BigDecimal,box:String,date:String,time:String,notes:String):Pair<String,JSONObject> = db.withTransaction {
  val repair=db.maintenanceDao().repair(repairId) ?: error("الصيانة غير موجودة")
  require(repair.source_sha256=="native8")
  val raw=JSONObject(repair.original_json)
  require(NativeMaintenanceProfitReader.extract(listOf(raw)).unresolved.isEmpty()) { "أدلة الصيانة تحتاج مراجعة" }
  repair.original_json to MaintenanceReturnRules.collection(raw,paymentId,amount,vault(box,repair.currency),date,time,notes,rates())
 }
 suspend fun saveCollection(repairId:String,expected:String,approved:String):JSONObject=db.withTransaction {
  val dao=db.maintenanceDao();val repair=dao.repair(repairId) ?: error("الصيانة غير موجودة")
  require(repair.source_sha256=="native8")
  val raw=JSONObject(repair.original_json);val payment=JSONObject(approved);val id=payment.getString("id")
  val prior=MaintenanceReturnRules.rows(raw,"nativePayments").firstOrNull { it.getString("id")==id }
  if(prior!=null) { require(prior.toString()==approved);return@withTransaction prior }
  require(repair.original_json==expected) { "تغيرت الصيانة؛ أعد المعاينة" }
  val record=MaintenanceReturnRules.collection(raw,id,MaintenanceReturnRules.n(payment,"amount"),
   vault(payment.getString("vault"),repair.currency),payment.getString("date"),payment.getString("time"),payment.getString("notes"),rates())
  require(record.toString()==approved) { "تغير سعر الصرف؛ أعد المعاينة" }
  val entries=raw.optJSONArray("nativePayments") ?: JSONArray();entries.put(record);raw.put("nativePayments",entries)
  raw.put("paymentStatus",if(MaintenanceReturnRules.due(raw)<BigDecimal("0.01")) "مدفوع بالكامل" else "دفعة جزئية")
  check(dao.appendSettlement(repairId,expected,raw.toString())==1) { "تغيرت الصيانة أثناء الحفظ" }
  val at=LocalDate.parse(record.getString("date")).atTime(LocalTime.parse(record.getString("time")))
   .atZone(ZoneId.of("Asia/Damascus")).toInstant().toEpochMilli()
  db.legacyPaymentDao().insertCashboxMovement(RoomCashboxMovement("repair-payment-cash-"+id,
   record.getString("vault"),repair.currency,record.getString("amount"),id,at,"تكملة دفع صيانة رقم "+repair.invoice_number))
  record
 }

}
