package com.alsafa.native8.sales

import com.alsafa.native8.data.migration.LegacyReadRepository
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext

/**
 * Source-derived read-only profit projection for v3.9.359.
 * Sales use the same event reader as the Room archive review.
 * Full statement approval remains blocked until repairs and operational coverage are verified.
 */
class LegacyHistoricalProfitProjection(private val legacy:LegacyReadRepository) {
 data class Result(
  val storeProfitUsd:BigDecimal,val repairProfitUsd:BigDecimal,
  val onlineProfitUsd:BigDecimal=BigDecimal.ZERO,
  val returnImpactUsd:BigDecimal,val expensesUsd:BigDecimal,
  val withdrawalsUsd:Map<String,BigDecimal>,
  val unresolved:List<String>,val onlineVerified:Boolean=false,
  val salesUnresolved:List<String> = emptyList(),val salesReturnImpactUsd:BigDecimal=BigDecimal.ZERO,
  val saleIds:Set<String> = emptySet(),val storeCapitalUsd:BigDecimal=BigDecimal.ZERO
 ) {
  val partialNetUsd get()=storeProfitUsd+onlineProfitUsd+repairProfitUsd+returnImpactUsd-expensesUsd
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
  val run=legacy.latestRunId() ?: return Result(storeProfitUsd=BigDecimal.ZERO,repairProfitUsd=BigDecimal.ZERO,
   returnImpactUsd=BigDecimal.ZERO,expensesUsd=BigDecimal.ZERO,withdrawalsUsd=emptyMap(),unresolved=listOf("No imported source"),salesUnresolved=listOf("No imported source"))
  val issues=mutableListOf<String>()
  val seen=mutableSetOf<String>()
  fun unique(section:String,row:LegacyReadRepository.LegacyRow):Boolean {
   val key="$section:${row.id ?: "ordinal-${row.ordinal}"}"
   if(!seen.add(key)) { issues+="Duplicate historical event: $key";return false }
   return true
  }
  val repairs=pages(run,"repairs")
  val repairById=repairs.mapNotNull { row ->
   (row.id ?: row.fields.optString("id").ifBlank { null })?.let { it to row.fields }
  }.toMap()
  require(repairById.size==repairs.count { it.id!=null }) { "Duplicate historical repair IDs" }
  var store=BigDecimal.ZERO
  var online=BigDecimal.ZERO
  var repair=BigDecimal.ZERO
  var returns=BigDecimal.ZERO
  var expense=BigDecimal.ZERO
  val withdrawals=mutableMapOf<String,BigDecimal>()
  fun inPeriod(date:String)=date>=from && date<=to
  fun checked(key:String,action:()->Unit) {
   try { action() } catch(e:Exception) { issues+="$key: ${e.message}" }
  }
  val salesRows=pages(run,"sales")
  val allPayments=pages(run,"payments")
  val allCash=pages(run,"cashTransactions")
  val allReturns=pages(run,"returns")
  val salesAudit=LegacyHistoricalSalesProfitReader.extractOriginal(
   salesRows.map { it.fields },allPayments.map { it.fields },
   allCash.map { it.fields },allReturns.map { it.fields })
  issues.addAll(salesAudit.unresolved)
  val salesSummary=FinancialSourceReconciliation.summarize(salesAudit.events,
   java.time.LocalDate.parse(from),java.time.LocalDate.parse(to))
  store=salesSummary.store
  online=salesSummary.online
  returns=salesSummary.returns
  // Original repairReceiptEvents: opening received on repair.date, later payments
  // on their own dates; original repairReceiptCost is proportional to invoice total.
  val receiptEvents=mutableListOf<Triple<String,JSONObject,BigDecimal>>()
  for(row in repairs) checked("repair:${row.id ?: row.ordinal}") {
   if(!unique("repairs",row)) return@checked
   val x=row.fields
   val received=n(x,"received")
   if(received>BigDecimal.ZERO && inPeriod(x.optString("date")))
    receiptEvents+=Triple(x.optString("date"),x,received)
  }
  for(row in allPayments) checked("payment:${row.id ?: row.ordinal}") {
   if(!unique("repair-payments",row)) return@checked
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
   // main.js repairPartsInvoiceTotal uses usedParts qty * price, not partsTotal.
   val usedParts=record.optJSONArray("usedParts")
   val parts=if(usedParts==null) BigDecimal.ZERO else
    (0 until usedParts.length()).fold(BigDecimal.ZERO) { sum,index ->
     val item=usedParts.getJSONObject(index)
     sum+n(item,"qty")*n(item,"price")
    }
   val total=if(record.isNull("laborPrice")) n(record,"price") else {
    val base=n(record,"laborPrice").max(BigDecimal.ZERO)+parts
    base*(BigDecimal.ONE+n(record,"taxRate").max(BigDecimal.ZERO).divide(BigDecimal(100),mc))
   }
   if(total<=BigDecimal.ZERO) return@checked // main.js: zero invoice total yields zero recognized revenue
   val ratio=amount.divide(total,mc).min(BigDecimal.ONE)
   val net=(total-n(record,"tax").max(BigDecimal.ZERO)).max(BigDecimal.ZERO)*ratio
   val cost=n(record,"cost")*amount.divide(total.max(BigDecimal.ONE),mc).min(BigDecimal.ONE)
   repair+=usd(net-cost,record)
  }
  for(row in pages(run,"expenses")) checked("expense:${row.id ?: row.ordinal}") {
   if(!unique("expenses",row)) return@checked
   val x=row.fields
   if(inPeriod(x.optString("date"))) expense+=usd(n(x,"amount"),x)
  }
  for(row in pages(run,"partnerWithdrawals")) checked("withdrawal:${row.id ?: row.ordinal}") {
   if(!unique("partnerWithdrawals",row)) return@checked
   val x=row.fields
   val id=x.optString("partnerId")
   if(inPeriod(x.optString("date")) && id in setOf("father","me","brother"))
    withdrawals[id]=(withdrawals[id] ?: BigDecimal.ZERO)+usd(n(x,"amount"),x)
  }
  for(row in allReturns.filter { it.fields.optString("referenceType")=="repair" }) checked("return:${row.id ?: row.ordinal}") {
   if(!unique("returns",row)) return@checked
   val x=row.fields
   if(!inPeriod(x.optString("date"))) return@checked
   val credit=if(x.has("netCreditAmount")) n(x,"netCreditAmount") else n(x,"creditAmount")
   val delta=if(x.optString("referenceType")=="repair") {
    val original=repairById[x.optString("referenceId")]
    val taxRate=original?.let { n(it,"taxRate") } ?: BigDecimal.ZERO
    val divisor=BigDecimal.ONE+taxRate.divide(BigDecimal(100),mc)
    val before=if(x.isNull("costRecognitionBefore")) BigDecimal.ONE else n(x,"costRecognitionBefore").max(BigDecimal.ZERO).min(BigDecimal.ONE)
    val after=if(x.isNull("costRecognitionAfter")) before else n(x,"costRecognitionAfter").max(BigDecimal.ZERO).min(BigDecimal.ONE)
    (n(x,"extraCollected")-n(x,"refundAmount")).divide(divisor,mc)+
     n(x,"returnedCost")*before-n(x,"replacementCost")*after
   } else {
    val ratio=if(x.isNull("recognizedRatio")) BigDecimal.ONE else n(x,"recognizedRatio").max(BigDecimal.ZERO).min(BigDecimal.ONE)
    (n(x,"returnedCost")-n(x,"replacementCost")-credit)*ratio
   }
   returns+=usd(delta,x)
  }
  check(legacy.latestRunId()==run) { "Historical source changed during read" }
  return Result(store,repair,online,returns,expense,withdrawals,issues,onlineVerified=false,salesUnresolved=salesAudit.unresolved,salesReturnImpactUsd=salesSummary.returns,saleIds=salesRows.map { it.fields.optString("id") }.toSet(),
   storeCapitalUsd=salesAudit.events.filter { it.stream==FinancialSourceReconciliation.Stream.LEGACY_STORE &&
    inPeriod(it.recognizedOn.toString()) }.fold(BigDecimal.ZERO) { sum,event -> sum+event.costUsd }) // Source parity remains unverified
 }
}
