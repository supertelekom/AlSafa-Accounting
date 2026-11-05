package com.alsafa.native8.sales

import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/** Exact v3.9.359 receipt/cost/return projection. Historical reads never post cash or stock. */
object LegacyMaintenanceProfitReader {
 private val mc=MathContext.DECIMAL128
 private val zero=BigDecimal.ZERO
 private val one=BigDecimal.ONE
 private fun n(x:JSONObject,key:String)=x.optString(key).toBigDecimalOrNull()
  ?: error("Missing maintenance $key")
 private fun optional(x:JSONObject,key:String)=if(x.isNull(key)) zero else n(x,key)
 private fun usd(value:BigDecimal,record:JSONObject,snapshot:JSONObject?)=value.multiply(
  LegacyFinancialExchange.usdPerUnit(record.getString("currency"),snapshot?.optString(record.getString("currency"))),mc)
 fun total(record:JSONObject):BigDecimal {
  if(record.isNull("laborPrice")) return n(record,"price")
  val parts=record.optJSONArray("usedParts")
  val partsTotal=if(parts==null) zero else (0 until parts.length()).fold(zero) { sum,i ->
   val line=parts.getJSONObject(i);sum+n(line,"qty")*n(line,"price") }
  val base=n(record,"laborPrice").max(zero)+partsTotal
  return base+base.multiply(optional(record,"taxRate").max(zero),mc).divide(BigDecimal(100),mc)
 }
 fun extract(records:List<JSONObject>,payments:List<JSONObject>,returns:List<JSONObject>):LegacyHistoricalSalesProfitReader.Audit {
  val events=mutableListOf<FinancialSourceReconciliation.Event>()
  val issues=mutableListOf<String>()
  val byId=records.groupBy { it.optString("id") }
  val linked=payments.filter { it.optString("referenceType")=="repair" }
  val duplicatePayments=linked.groupBy { it.optString("id") }.filter { it.key.isBlank() || it.value.size>1 }.keys
  val repairReturns=returns.filter { it.optString("referenceType")=="repair" }
  val duplicateReturns=repairReturns.groupBy { it.optString("id") }.filter { it.key.isBlank() || it.value.size>1 }.keys
  val rejected=mutableSetOf<String>()
  for(p in linked) if(p.optString("referenceId") !in byId) issues+="Orphan repair payment: ${p.optString("id")}"
  for((id,rows) in byId) try {
   require(id.isNotBlank() && rows.size==1) { "Missing/duplicate repair ID" }
   val record=rows.single()
   val pending=mutableListOf<FinancialSourceReconciliation.Event>()
   val receipts=linked.filter { it.optString("referenceId")==id }
   require(receipts.none { it.optString("id") in duplicatePayments }) { "Duplicate payment ID" }
   fun receipt(key:String,date:String,amount:BigDecimal,snapshot:JSONObject?) {
    if(amount<=zero) return
    val total=total(record).max(zero)
    val cost=n(record,"cost").also { require(it>=zero) }
    val tax=if(record.isNull("tax")) {
     require(optional(record,"taxRate").signum()==0) { "Missing tax amount" };zero
    } else n(record,"tax").max(zero)
    val revenue=if(total>zero) (total-tax).max(zero)*amount.divide(total,mc).min(one) else zero
    // Original denominator is max(1, invoice total), including sub-dollar repairs.
    val allocated=cost*amount.divide(total.max(one),mc).min(one)
    pending+=FinancialSourceReconciliation.Event(key,FinancialSourceReconciliation.Stream.REPAIR,
     LocalDate.parse(date),usd(revenue,record,snapshot),usd(allocated,record,snapshot))
   }
   receipt("legacy:repair:$id:initial",record.getString("date"),n(record,"received"),record.optJSONObject("rateSnapshot"))
   for(p in receipts) {
    if(p.optString("paymentType")=="repair-exchange-difference") continue // included once in return delta
    require(p.optString("currency",record.getString("currency"))==record.getString("currency")) { "Receipt currency differs from invoice" }
    receipt("legacy:repair:$id:payment:${p.getString("id")}",p.getString("date"),n(p,"amount"),
     p.optJSONObject("rateSnapshot") ?: record.optJSONObject("rateSnapshot"))
   }
   events.addAll(pending)
  } catch(e:Exception) { rejected+=id;issues+="Repair $id: ${e.message}" }
  for(r in repairReturns) try {
   val id=r.getString("id")
   require(id !in duplicateReturns) { "Duplicate return ID" }
   val original=r.getString("referenceId")
   require(original !in rejected) { "Unresolved repair" }
   val record=byId[original]?.singleOrNull() ?: error("Original repair missing")
   val divisor=one+optional(record,"taxRate").max(zero).divide(BigDecimal(100),mc)
   val before=if(r.isNull("costRecognitionBefore")) one else n(r,"costRecognitionBefore").coerceIn(zero,one)
   val after=if(r.isNull("costRecognitionAfter")) before else n(r,"costRecognitionAfter").coerceIn(zero,one)
   val delta=(n(r,"extraCollected")-n(r,"refundAmount")).divide(divisor,mc)+
    n(r,"returnedCost")*before-n(r,"replacementCost")*after
   events+=FinancialSourceReconciliation.Event("legacy:repair-return:$id",FinancialSourceReconciliation.Stream.RETURN,
    LocalDate.parse(r.getString("date")),zero,zero,usd(delta,r,r.optJSONObject("rateSnapshot")))
  } catch(e:Exception) { issues+="Repair return ${r.optString("id")}: ${e.message}" }
  check(events.map { it.sourceKey }.distinct().size==events.size)
  return LegacyHistoricalSalesProfitReader.Audit(events,issues.distinct())
 }
}
