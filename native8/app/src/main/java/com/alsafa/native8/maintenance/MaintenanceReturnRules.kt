package com.alsafa.native8.maintenance

import org.json.JSONObject
import org.json.JSONArray
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.LocalTime
import com.alsafa.native8.sales.LegacyMaintenanceProfitReader
import com.alsafa.native8.sales.LegacyFinancialExchange

/** main.js repairReturnCalculation/saveRepairReturn, without database mutations. */
object MaintenanceReturnRules {
 private val z=BigDecimal.ZERO
 private val one=BigDecimal.ONE
 private val mc=MathContext.DECIMAL128
 data class Request(val id:String,val kind:String,val partIndex:Int,val qty:BigDecimal,
  val credit:BigDecimal,val restock:Boolean,val replacementId:String,val replacementQty:BigDecimal,
  val replacementAmount:BigDecimal,val extra:BigDecimal,val vault:String,val date:String,val time:String,val notes:String)
 fun n(x:JSONObject,k:String)=x.optString(k).toBigDecimalOrNull() ?: error("قيمة غير موثوقة: $k")
 fun rows(x:JSONObject,key:String):List<JSONObject> = (x.optJSONArray(key) ?: JSONArray()).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
 fun effective(raw:JSONObject)= (LegacyMaintenanceProfitReader.total(raw)-rows(raw,"nativeReturns").fold(z) { a,r -> a+n(r,"creditAmount") }).max(z)
 fun paid(raw:JSONObject)= (n(raw,"received")+rows(raw,"nativePayments").fold(z) { a,p -> a+n(p,"amount") }+
  rows(raw,"nativeReturns").fold(z) { a,r -> a+n(r,"extraCollected")-n(r,"refundAmount") }).max(z)
 fun due(raw:JSONObject)=(effective(raw)-paid(raw)).max(z)
 private fun positiveQty(q:BigDecimal) { require(q>z && q.stripTrailingZeros().scale()<=0) { "الكمية يجب أن تكون عدداً صحيحاً موجباً" } }
 private fun converted(value:BigDecimal,from:String,to:String,snap:JSONObject?):BigDecimal {
  if(from==to) return value
  val direct=snap?.optString("TRY_SYP")?.toBigDecimalOrNull()
  if(direct!=null && direct>z) {
   if(from=="TRY" && to=="SYP") return value*direct
   if(from=="SYP" && to=="TRY") return value.divide(direct,mc)
  }
  return value.multiply(LegacyFinancialExchange.usdPerUnit(from,snap?.optString(from)),mc)
   .divide(LegacyFinancialExchange.usdPerUnit(to,snap?.optString(to)),mc)
 }
 fun collection(raw:JSONObject,id:String,amount:BigDecimal,vault:String,date:String,time:String,notes:String,rates:JSONObject):JSONObject {
  require(raw.getString("status")=="تم التسليم")
  require(id.isNotBlank() && id.all { it.isLetterOrDigit() || it=='-' })
  LocalDate.parse(date);LocalTime.parse(time)
  require(date>=raw.getString("deliveredDate")) { "تاريخ القبض يسبق التسليم" }
  val history=rows(raw,"nativePayments")+rows(raw,"nativeReturns")
  require(history.none { it.getString("id")==id || it.getString("date")>date }) { "المعرف مكرر أو التاريخ يسبق آخر تسوية" }
  require(amount>z && amount<=due(raw)) { "المبلغ يجب أن يكون موجباً وألا يتجاوز المتبقي" }
  val unit=raw.getString("currency")
  LegacyFinancialExchange.usdPerUnit(unit,rates.optString(unit))
  return JSONObject().put("id",id).put("referenceType","repair").put("referenceId",raw.getString("id"))
   .put("amount",amount.toPlainString()).put("currency",unit).put("paymentCurrency",unit)
   .put("paymentAmount",amount.toPlainString()).put("vault",vault).put("date",date).put("time",time)
   .put("notes",notes).put("rateSnapshot",rates).put("remainingAfter",(due(raw)-amount).toPlainString())
 }
 fun calculate(raw:JSONObject,q:Request,replacement:JSONObject?,rates:JSONObject):JSONObject {
  require(raw.getString("status")=="تم التسليم") { "المرتجع للصيانة المسلّمة فقط" }
  require(q.id.isNotBlank() && q.id.all { it.isLetterOrDigit() || it=='-' })
  require(q.kind in setOf("return","exchange"))
  require(q.notes.isNotBlank()) { "التفاصيل مطلوبة" }
  LocalDate.parse(q.date);LocalTime.parse(q.time)
  require(q.date>=raw.getString("deliveredDate")) { "تاريخ المرتجع يسبق التسليم" }
  val history=rows(raw,"nativeReturns")
  require((history+rows(raw,"nativePayments")).none { it.getString("id")==q.id }) { "معرف العملية مستخدم" }
  val dates=history.map { it.getString("date") }+rows(raw,"nativePayments").map { it.getString("date") }
  require(dates.none { it>q.date }) { "تاريخ العملية يسبق آخر تسوية؛ راجع التاريخ" }
  val parts=rows(raw,"usedParts")
  require(q.partIndex==-1 || q.partIndex in parts.indices)
  val part=parts.getOrNull(q.partIndex)
  val unit=raw.getString("currency")
  val tax=if(raw.isNull("taxRate")) z else n(raw,"taxRate").max(z).divide(BigDecimal(100),mc)
  val factor=one+tax
  val qty=if(part!=null) q.qty.also { positiveQty(it) } else z
  if(part!=null) {
   val used=history.filter { it.optInt("repairPartIndex",-1)==q.partIndex }.fold(z) { a,r -> a+n(r,"returnedPartQty") }
   require(qty<=n(part,"qty")-used) { "الكمية تجاوزت المتبقي للمرتجع" }
  }
  require(q.credit>=z && q.replacementAmount>=z && q.extra>=z)
  val credit=if(part==null) q.credit else n(part,"price")*qty*factor
  val replace=if(q.kind=="exchange") replacement else null
  require(q.kind!="exchange" || q.replacementId.isEmpty() || replace!=null) { "القطعة البديلة غير موجودة" }
  val rqty=if(replace!=null) q.replacementQty.also { positiveQty(it) } else z
  if(replace!=null) {
   require(n(replace,"stock")>=rqty) { "مخزون البديل غير كافٍ" }
   require(n(replace,"salePrice")>=z && n(replace,"cost")>=z) { "سعر أو تكلفة البديل غير صالحة" }
  }
  val replacementNet=if(replace!=null) converted(n(replace,"salePrice"),replace.getString("currency"),unit,raw.optJSONObject("rateSnapshot"))*rqty
   else if(q.kind=="exchange") q.replacementAmount.divide(factor,mc) else z
  val replacementValue=if(replace!=null) replacementNet*factor else if(q.kind=="exchange") q.replacementAmount else z
  require(credit>z || replacementValue>z) { "أدخل قيمة المرتجع أو البديل" }
  val current=effective(raw);val paid=paid(raw);val debt=(current-paid).max(z)
  val net=credit-replacementValue
  require(net<=current+BigDecimal("0.001")) { "قيمة المرتجع الصافية أكبر من إجمالي الصيانة" }
  val after=(current-net).max(z)
  val refund=(paid-after).max(z)
  val extraDue=(after-paid).max(z)
  val extra=q.extra.min(extraDue)
  val paidAfter=(paid-refund+extra).max(z)
  val beforeRatio=if(current>z) paid.divide(current,mc).min(one) else z
  val afterRatio=if(after>z) paidAfter.divide(after,mc).min(one) else z
  val restored=part!=null && q.restock
  val returnedCost=if(restored) n(part!!,"cost")*qty else z
  val replacementCost=if(replace!=null) converted(n(replace,"cost"),replace.getString("currency"),unit,raw.optJSONObject("rateSnapshot"))*rqty else z
  LegacyFinancialExchange.usdPerUnit(unit,rates.optString(unit))
  val r=JSONObject().put("id",q.id).put("referenceType","repair").put("referenceId",raw.getString("id"))
   .put("invoiceNo",raw.get("invoiceNo")).put("kind",q.kind).put("currency",unit)
   .put("repairPartIndex",q.partIndex).put("returnedPartId",part?.optString("partId") ?: "")
   .put("returnedPartName",part?.optString("name") ?: "").put("returnedPartRestocked",restored)
   .put("replacementPartId",replace?.getString("id") ?: "").put("replacementPartName",replace?.optString("name") ?: "")
   .put("vault",q.vault).put("date",q.date).put("time",q.time).put("notes",q.notes).put("rateSnapshot",rates)
  for((key,value) in mapOf("returnedPartQty" to qty,"replacementPartQty" to rqty,
   "taxEffect" to ((replacementValue-replacementNet)-(credit-credit.divide(factor,mc))),"creditAmount" to net,"netCreditAmount" to (credit.divide(factor,mc)-replacementNet),
   "returnedValue" to credit,"replacementValue" to replacementValue,"returnedCost" to returnedCost,"replacementCost" to replacementCost,
   "refundAmount" to refund,"extraCollected" to extra,"cashEffect" to (extra-refund),"currentTotalBefore" to current,
   "newTotalAfter" to after,"paidBefore" to paid,"paidAfter" to paidAfter,"costRecognitionBefore" to beforeRatio,
   "costRecognitionAfter" to afterRatio,"debtBefore" to debt,"debtReduction" to debt.min(net).max(z),
   "remainingAfter" to (after-paidAfter).max(z),"extraDue" to extraDue)) r.put(key,value.toPlainString())
  return r
 }
}
