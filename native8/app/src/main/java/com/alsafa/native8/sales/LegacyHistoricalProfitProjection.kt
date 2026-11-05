package com.alsafa.native8.sales

import com.alsafa.native8.data.migration.LegacyReadRepository
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext

/**
 * Source-derived read-only profit projection for v3.9.359.
 * The online opening-receipt reconstruction is deliberately excluded until
 * cashTransactions/payment reconciliation exactly matches main.js.
 */
class LegacyHistoricalProfitProjection(private val legacy:LegacyReadRepository) {
 data class Result(
  val storeProfitUsd:BigDecimal,val repairProfitUsd:BigDecimal,
  val returnImpactUsd:BigDecimal,val expensesUsd:BigDecimal,
  val withdrawalsUsd:Map<String,BigDecimal>,
  val unresolved:List<String>,val onlineVerified:Boolean=false
 ) {
  val partialNetUsd get()=storeProfitUsd+repairProfitUsd+returnImpactUsd-expensesUsd
  val complete get()=onlineVerified && unresolved.isEmpty()
 }
 private val mc=MathContext.DECIMAL128
 private fun n(obj:JSONObject,key:String):BigDecimal =
  obj.optString(key,"0").ifBlank { "0" }.toBigDecimal()
 private fun usd(value:BigDecimal,obj:JSONObject):BigDecimal {
  val unit=obj.optString("currency","USD")
  val snap=obj.optJSONObject("rateSnapshot")
  val rate=LegacyFinancialExchange.usdPerUnit(unit,snap?.optString(unit))
  return value.multiply(rate,mc)
 }
 private fun pages(run:Long,section:String):List<LegacyReadRepository.LegacyRow> {
  val result=mutableListOf<LegacyReadRepository.LegacyRow>()
  var offset=0
  while(true) {
   val page=legacy.page(run,section,offset,500)
   result.addAll(page.rows)
   offset+=page.rows.size
   if(offset>=page.total) return result
   check(page.rows.isNotEmpty()) { "Missing source rows: $section" }
  }
  return result
 }
 fun read(from:String,to:String):Result {
  require(from<=to)
  val run=legacy.latestRunId() ?: return Result(BigDecimal.ZERO,BigDecimal.ZERO,
   BigDecimal.ZERO,BigDecimal.ZERO,emptyMap(),listOf("No imported source"))
  val issues=mutableListOf<String>()
  val repairs=pages(run,"repairs")
  val repairById=repairs.mapNotNull { row ->
   row.id?.let { it to row.fields }
  }.toMap()
  var store=BigDecimal.ZERO
  var repair=BigDecimal.ZERO
  var returns=BigDecimal.ZERO
  var expense=BigDecimal.ZERO
  val withdrawals=mutableMapOf<String,BigDecimal>()
  fun inPeriod(date:String)=date>=from && date<=to
  fun checked(key:String,action:()->Unit) {
   try { action() } catch(e:Exception) { issues+="$key: ${e.message}" }
  }
  for(row in pages(run,"sales")) checked("sale:${row.id ?: row.ordinal}") {
   val x=row.fields
   if(!inPeriod(x.optString("date")) || x.optString("saleType","store")=="online") return@checked
   val items=x.getJSONArray("items")
   var cost=BigDecimal.ZERO
   for(i in 0 until items.length()) {
    val item=items.getJSONObject(i)
    cost+=n(item,"cost")*n(item,"qty")
   }
   val amount=n(x,"total")-n(x,"tax")-cost
   store+=usd(amount,x)
  }
  // Original repairReceiptEvents: opening received on repair.date, later payments
  // on their own dates; original repairReceiptCost is proportional to invoice total.
  val receiptEvents=mutableListOf<Triple<String,JSONObject,BigDecimal>>()
  for(row in repairs) checked("repair:${row.id ?: row.ordinal}") {
   val x=row.fields
   val received=n(x,"received")
   if(received>BigDecimal.ZERO && inPeriod(x.optString("date")))
    receiptEvents+=Triple(x.optString("date"),x,received)
  }
  for(row in pages(run,"payments")) checked("payment:${row.id ?: row.ordinal}") {
   val x=row.fields
   if(x.optString("referenceType")!="repair" ||
      x.optString("paymentType")=="repair-exchange-difference" ||
      !inPeriod(x.optString("date"))) return@checked
   val amount=n(x,"amount")
   if(amount<=BigDecimal.ZERO) return@checked
   val record=repairById[x.optString("referenceId")]
    ?: error("Repair reference not found")
   val event=JSONObject(record.toString())
   if(x.has("rateSnapshot")) event.put("rateSnapshot",x.getJSONObject("rateSnapshot"))
   receiptEvents+=Triple(x.optString("date"),event,amount)
  }
  for((_,record,amount) in receiptEvents) checked("repair-receipt:${record.optString("id")}") {
   val total=n(record,"price")
   require(total>BigDecimal.ZERO) { "Repair invoice total missing" }
   val ratio=amount.divide(total,mc).min(BigDecimal.ONE)
   val net=(total-n(record,"tax"))*amount.divide(total,mc)
   val cost=n(record,"cost")*ratio
   repair+=usd(net-cost,record)
  }
  for(row in pages(run,"expenses")) checked("expense:${row.id ?: row.ordinal}") {
   val x=row.fields
   if(inPeriod(x.optString("date"))) expense+=usd(n(x,"amount"),x)
  }
  for(row in pages(run,"partnerWithdrawals")) checked("withdrawal:${row.id ?: row.ordinal}") {
   val x=row.fields
   val id=x.optString("partnerId")
   if(inPeriod(x.optString("date")) && id in setOf("father","me","brother"))
    withdrawals[id]=(withdrawals[id] ?: BigDecimal.ZERO)+usd(n(x,"amount"),x)
  }
  for(row in pages(run,"returns")) checked("return:${row.id ?: row.ordinal}") {
   val x=row.fields
   if(!inPeriod(x.optString("date"))) return@checked
   val credit=if(x.has("netCreditAmount")) n(x,"netCreditAmount") else n(x,"creditAmount")
   val delta=if(x.optString("referenceType")=="repair") {
    val original=repairById[x.optString("referenceId")]
    val taxRate=original?.let { n(it,"taxRate") } ?: BigDecimal.ZERO
    val divisor=BigDecimal.ONE+taxRate.divide(BigDecimal(100),mc)
    val before=if(x.isNull("costRecognitionBefore")) BigDecimal.ONE else n(x,"costRecognitionBefore")
    val after=if(x.isNull("costRecognitionAfter")) before else n(x,"costRecognitionAfter")
    (n(x,"extraCollected")-n(x,"refundAmount")).divide(divisor,mc)+
     n(x,"returnedCost")*before-n(x,"replacementCost")*after
   } else {
    val ratio=if(x.isNull("recognizedRatio")) BigDecimal.ONE else n(x,"recognizedRatio")
    (n(x,"returnedCost")-n(x,"replacementCost")-credit)*ratio
   }
   returns+=usd(delta,x)
  }
  return Result(store,repair,returns,expense,withdrawals,issues)
 }
}
