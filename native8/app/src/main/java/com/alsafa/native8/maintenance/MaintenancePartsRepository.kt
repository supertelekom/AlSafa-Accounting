package com.alsafa.native8.maintenance

import com.alsafa.native8.sales.AppDatabase
import androidx.room.withTransaction
import org.json.JSONObject
import org.json.JSONArray
import java.math.BigDecimal
import java.util.UUID

/** Inventory rules derived from legacy v3.9.359; all mutations use the unified Room database. */
class MaintenancePartsRepository(private val db: AppDatabase) {
 private val dao get() = db.maintenanceDao()
 private fun amount(value:String):BigDecimal = value.trim().toBigDecimalOrNull()
  ?: throw IllegalArgumentException("قيمة رقمية غير صحيحة")
 private fun nonnegative(value:String):BigDecimal = amount(value).also { require(it>=BigDecimal.ZERO) { "لا يسمح بالقيم السالبة" } }
 private fun integer(value:String):BigDecimal = nonnegative(value).also { require(it.stripTrailingZeros().scale()<=0) { "الكمية يجب أن تكون عدداً صحيحاً" } }
 private fun canonical(value:BigDecimal)=value.stripTrailingZeros().toPlainString()
 suspend fun list(query:String=""):List<MaintenancePartEntity> = dao.searchParts(query.trim())
 suspend fun save(id:String?,name:String,category:String,barcode:String,stock:String,minStock:String,currency:String,cost:String,salePrice:String,notes:String):String = db.withTransaction {
  val clean=name.trim()
  require(clean.isNotBlank()) { "اسم القطعة مطلوب" }
  val qty=integer(stock); val min=integer(minStock);val buy=nonnegative(cost);val sell=nonnegative(salePrice)
  val old=id?.let { dao.part(it) }
  if(id!=null) require(old!=null) { "القطعة غير موجودة" }
  val normalizedBarcode=barcode.trim()
  require(dao.partNameConflict(clean,id ?: "")==0) { "اسم قطعة الصيانة مسجل مسبقاً" }
  if(normalizedBarcode.isNotBlank()) {
   require(dao.partBarcodeConflict(normalizedBarcode,id ?: "")==0) { "الباركود مستخدم في قطعة صيانة أخرى" }
   require(dao.productBarcodeConflict(normalizedBarcode)==0) { "الباركود مستخدم في المنتجات" }
  }
  val partId=old?.id ?: "maintenance-part-"+UUID.randomUUID()
  val json=old?.original_json?.let { JSONObject(it) } ?: JSONObject()
  json.put("id",partId);json.put("name",clean);json.put("category",category)
  json.put("barcode",normalizedBarcode);json.put("stock",canonical(qty))
  json.put("minStock",canonical(min));json.put("currency",currency)
  json.put("cost",canonical(buy));json.put("salePrice",canonical(sell))
  json.put("notes",notes);json.put("createdAt",old?.created_at ?: System.currentTimeMillis())
  val entity=MaintenancePartEntity(partId,old?.source_sha256 ?: "native8",clean,category,normalizedBarcode,canonical(qty),currency,canonical(buy),canonical(sell),old?.created_at ?: System.currentTimeMillis(),json.toString())
  if(old==null) dao.insertNewPart(entity) else {
   val changed=if(old.source_sha256=="native8") dao.updateNewPart(entity,old.original_json)
    else dao.updateImportedPartFields(entity.id,entity.name,entity.category,entity.barcode,entity.stock,entity.currency,entity.cost,entity.sale_price,entity.original_json,old.original_json)
   check(changed==1) { "تغيرت القطعة أثناء التعديل، أعد تحميلها" }
  }
  partId
 }
 suspend fun delete(id:String) = db.withTransaction {
  val part=dao.part(id) ?: error("القطعة غير موجودة")
  require(part.source_sha256=="native8") { "لا يمكن حذف قطعة مستعادة" }
  require(dao.partReferenced(id)==0) { "القطعة مرتبطة بفواتير صيانة؛ لا يمكن حذفها" }
  check(dao.deleteNewPart(id)==1)
 }
 /** Picker changes the invoice draft only; legacy stock deduction happens at delivery. */
 suspend fun saveDraft(repairId:String,parts:List<SelectedPart>) = db.withTransaction {
  val repair=dao.repair(repairId) ?: error("فاتورة الصيانة غير موجودة")
  require(repair.source_sha256=="native8" && repair.status!="تم التسليم") { "الفاتورة غير قابلة للتعديل" }
  val old=JSONObject(repair.original_json)
  require(!old.optBoolean("partsStockApplied",false)) { "تم اعتماد مخزون هذه الصيانة مسبقاً" }
  val used=JSONArray();var total=BigDecimal.ZERO;var costTotal=BigDecimal.ZERO
  val seen=mutableSetOf<String>()
  for(line in parts) {
   require(seen.add(line.partId)) { "القطعة مكررة" }
   val part=dao.part(line.partId) ?: error("القطعة غير موجودة")
   val qty=integer(line.qty);require(qty>BigDecimal.ZERO) { "الكمية يجب أن تكون أكبر من صفر" }
   require(qty<=integer(part.stock)) { "الكمية غير متوفرة: ${part.name}" }
   val price=nonnegative(line.price)
   val originalCost=nonnegative(part.cost)
   val costRate=com.alsafa.native8.sales.ExchangeRateSettings(AppDatabase.cashContext()).rate(part.currency,repair.currency)
   val cost=originalCost.multiply(costRate)
   used.put(JSONObject().put("partId",part.id).put("name",part.name).put("category",part.category)
    .put("barcode",part.barcode).put("qty",canonical(qty)).put("price",canonical(price))
    .put("cost",canonical(cost)).put("currency",repair.currency)
    .put("costSnapshotVerified",true).put("sourceUnitCost",part.cost)
    .put("sourceCostCurrency",part.currency).put("costExchangeRate",canonical(costRate)))
   total=total.add(qty.multiply(price));costTotal=costTotal.add(qty.multiply(cost))
  }
  old.put("usedParts",used);old.put("partsTotal",canonical(total));old.put("partsCost",canonical(costTotal))
  check(dao.updateParts(repairId,repair.original_json,old.toString())==1) { "تغيرت الفاتورة؛ أعد تحميلها" }
 }

 /** Delivery-time stock application. Repeated calls are idempotent. */
 suspend fun applyAtDelivery(repairId:String) = db.withTransaction {
  val repair=dao.repair(repairId) ?: error("فاتورة الصيانة غير موجودة")
  require(repair.source_sha256=="native8") { "لا يمكن تعديل فاتورة تاريخية" }
  val raw=JSONObject(repair.original_json)
  if(raw.optBoolean("partsStockApplied",false)) return@withTransaction
  val lines=raw.optJSONArray("usedParts") ?: JSONArray()
  val adjustments=mutableListOf<Triple<MaintenancePartEntity,BigDecimal,BigDecimal>>()
  for(i in 0 until lines.length()) {
   val item=lines.getJSONObject(i)
   val part=dao.part(item.getString("partId")) ?: error("قطعة الصيانة غير موجودة")
   val qty=integer(item.getString("qty"))
   require(qty>BigDecimal.ZERO) { "كمية غير صحيحة" }
   val before=integer(part.stock)
   require(before>=qty) { "المخزون غير كافٍ: ${part.name}" }
   adjustments.add(Triple(part,before,before.subtract(qty)))
  }
  for((part,before,after) in adjustments) {
   check(dao.changePartStock(part.id,canonical(before),canonical(after))==1) { "تغير مخزون القطعة: ${part.name}" }
  }
  raw.put("partsStockApplied",true)
  check(dao.updateParts(repairId,repair.original_json,raw.toString())==1) { "تغيرت الفاتورة أثناء الخصم" }
 }
 /** Reverse an applied deduction once, in the same Room transaction. */
 suspend fun restoreOnReversal(repairId:String) = db.withTransaction {
  val repair=dao.repair(repairId) ?: error("فاتورة الصيانة غير موجودة")
  require(repair.source_sha256=="native8") { "لا يمكن تعديل فاتورة تاريخية" }
  val raw=JSONObject(repair.original_json)
  if(!raw.optBoolean("partsStockApplied",false)) return@withTransaction
  val lines=raw.optJSONArray("usedParts") ?: JSONArray()
  for(i in 0 until lines.length()) {
   val item=lines.getJSONObject(i)
   val part=dao.part(item.getString("partId")) ?: error("قطعة الصيانة غير موجودة")
   val before=integer(part.stock)
   val after=before.add(integer(item.getString("qty")))
   check(dao.changePartStock(part.id,canonical(before),canonical(after))==1) { "تغير مخزون القطعة" }
  }
  raw.put("partsStockApplied",false)
  check(dao.updateParts(repairId,repair.original_json,raw.toString())==1) { "تغيرت الفاتورة أثناء الاسترجاع" }
 }
 suspend fun refreshDraftTotal(id:String)=db.withTransaction {
  val repair=dao.repair(id) ?: error("الصيانة غير موجودة")
  require(repair.source_sha256=="native8" && repair.status!="تم التسليم")
  val raw=JSONObject(repair.original_json)
  val total=com.alsafa.native8.sales.LegacyMaintenanceProfitReader.total(raw)
  raw.put("price",total.toPlainString())
  check(dao.updateDraftPrice(id,repair.original_json,raw.toString(),total.toPlainString())==1) { "تغيرت الصيانة أثناء الحفظ" }
 }
 data class SelectedPart(val partId:String,val qty:String,val price:String)
}
