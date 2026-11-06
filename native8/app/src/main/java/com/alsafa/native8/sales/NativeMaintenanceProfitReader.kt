package com.alsafa.native8.sales

import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/** Delivery evidence only; never replays a stock deduction or cash receipt. */
object NativeMaintenanceProfitReader {
 private val zero=BigDecimal.ZERO
 private val mc=MathContext.DECIMAL128
 private fun n(x:JSONObject,key:String)=x.optString(key).toBigDecimalOrNull() ?: error("Missing delivery $key")
 fun snapshot(raw:JSONObject,unit:String,rates:JSONObject):JSONObject {
  val copy=JSONObject(raw.toString())
  val parts=copy.getJSONArray("usedParts")
  var cost=zero
  for(i in 0 until parts.length()) {
   val line=parts.getJSONObject(i)
   require(line.optBoolean("costSnapshotVerified") && line.getString("currency")==unit) {
    "تكلفة قطعة الصيانة تحتاج إعادة اختيار القطعة لتثبيت عملتها وتكلفتها" }
   val quantity=n(line,"qty");val unitCost=n(line,"cost")
   require(quantity>zero && unitCost>=zero)
   cost+=quantity.multiply(unitCost,mc)
  }
  val manual=if(copy.isNull("manualCost")) zero else n(copy,"manualCost")
  require(manual>=zero)
  LegacyFinancialExchange.usdPerUnit(unit,rates.optString(unit))
  copy.put("partsCost",cost.toPlainString()).put("manualCost",manual.toPlainString())
   .put("cost",(cost+manual).toPlainString()).put("rateSnapshot",rates)
   .put("financialSnapshotVersion",1)
  return copy
 }
 fun extract(records:List<JSONObject>):LegacyHistoricalSalesProfitReader.Audit {
  val events=mutableListOf<FinancialSourceReconciliation.Event>();val issues=mutableListOf<String>()
  val duplicates=records.groupBy { it.optString("id") }.filter { it.key.isBlank() || it.value.size>1 }.keys
  for(raw in records) try {
   val id=raw.getString("id");require(id !in duplicates) { "Missing/duplicate native repair ID" }
   if(raw.optString("status")!="تم التسليم") {
    require(n(raw,"received").signum()==0) { "Receipt on undelivered repair" };continue
   }
   val date=LocalDate.parse(raw.getString("deliveredDate"))
   val unit=raw.getString("currency")
   if(raw.optInt("financialSnapshotVersion")==0) {
    // No requirement for later Native 8-only flags or paymentLines in older records.
    // Preserve explicit cost/received/FX; missing amounts are not silently invented.
    fun oldRows(key:String)=raw.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
    val old=LegacyMaintenanceProfitReader.extract(listOf(JSONObject(raw.toString()).put("date",date.toString())),oldRows("nativePayments"),oldRows("nativeReturns"))
    require(old.unresolved.isEmpty()) { old.unresolved.joinToString() }
    events+=old.events.map { e -> e.copy(sourceKey=if(e.sourceKey=="legacy:repair:$id:initial") "native:repair:$id:delivery" else e.sourceKey.replaceFirst("legacy:","native:")) }
    continue
   }
   val parts=raw.getJSONArray("usedParts")
   // Older native deliveries with no parts and explicit zero cost are unambiguous.
   // Do not invent FX or accept old drafts whose part currency was not recorded reliably.
   val verified=raw.optInt("financialSnapshotVersion")==1
   require(verified || (parts.length()==0 && n(raw,"cost").signum()==0)) { "Unverified delivered parts cost" }
   if(verified) {
    val checked=snapshot(raw,unit,raw.getJSONObject("rateSnapshot"))
    require(n(checked,"cost").compareTo(n(raw,"cost"))==0) { "Delivery cost differs from part evidence" }
   }
   val paymentLines=raw.getJSONArray("paymentLines")
   // Explicit zero-value deliveries carry no synthetic receipt/profit event.
   if(LegacyMaintenanceProfitReader.total(raw).signum()==0 && n(raw,"received").signum()==0 && paymentLines.length()==0) {
    require(n(raw,"price").signum()==0) { "Zero delivery total mismatch" }
   } else {
   require(paymentLines.length()>0) { "Missing actual delivery receipt" }
   var credited=zero
   for(i in 0 until paymentLines.length()) {
    val p=paymentLines.getJSONObject(i)
    require(n(p,"amount")>zero && n(p,"fxRate")>zero) { "Invalid actual receipt" }
    require(p.getString("currency") in setOf("USD","TRY","SYP"))
    val credit=n(p,"invoiceCredit");require(credit>zero) { "Invalid invoice credit" }
    if(p.getString("currency")==unit) require(n(p,"fxRate").compareTo(BigDecimal.ONE)==0) { "Same-currency FX must be one" }
    credited+=credit
   }
   require(credited>zero && credited.compareTo(n(raw,"received"))==0) { "Receipt credit differs from delivery" }
   val total=LegacyMaintenanceProfitReader.total(raw)
   require(total.compareTo(n(raw,"price"))==0) { "Delivery total differs from parts/labour/tax" }
   require(credited<=total) { "Delivery receipt exceeds invoice total" }
   }
   fun rows(key:String)=raw.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
   val audit=LegacyMaintenanceProfitReader.extract(listOf(JSONObject(raw.toString()).put("date",date.toString())),rows("nativePayments"),rows("nativeReturns"))
   require(audit.unresolved.isEmpty()) { audit.unresolved.joinToString() }
   events+=audit.events.map { event -> event.copy(sourceKey=if(event.sourceKey=="legacy:repair:$id:initial")
    "native:repair:$id:delivery" else event.sourceKey.replaceFirst("legacy:","native:")) }
  } catch(e:Exception) { issues+="Native repair ${raw.optString("id")}: ${e.message}" }
  return LegacyHistoricalSalesProfitReader.Audit(events,issues.distinct())
 }
}
