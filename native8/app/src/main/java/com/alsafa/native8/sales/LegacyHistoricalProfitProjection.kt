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
  val onlineProfitUsd:BigDecimal=BigDecimal.ZERO,
  val returnImpactUsd:BigDecimal,val expensesUsd:BigDecimal,
  val withdrawalsUsd:Map<String,BigDecimal>,
  val unresolved:List<String>,val onlineVerified:Boolean=false
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
   returnImpactUsd=BigDecimal.ZERO,expensesUsd=BigDecimal.ZERO,withdrawalsUsd=emptyMap(),unresolved=listOf("No imported source"))
  val issues=mutableListOf<String>()
  val seen=mutableSetOf<String>()
  fun unique(section:String,row:LegacyReadRepository.LegacyRow):Boolean {
   val key="$section:${row.id ?: "ordinal-${row.ordinal}"}"
   if(!seen.add(key)) { issues+="Duplicate historical event: $key";return false }
   return true
  }
  val repairs=pages(run,"repairs")
  val repairById=repairs.mapNotNull { row ->
   row.id?.let { it to row.fields }
  }.toMap()
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
  for(row in salesRows) checked("sale:${row.id ?: row.ordinal}") {
   val x=row.fields
   if(!unique("sales",row) || !inPeriod(x.optString("date")) || x.optString("saleType","store")=="online") return@checked
   val items=x.getJSONArray("items")
   var cost=BigDecimal.ZERO
   for(i in 0 until items.length()) {
    val item=items.getJSONObject(i)
    cost+=n(item,"cost")*n(item,"qty")
   }
   val amount=n(x,"total")-n(x,"tax")-cost
   store+=usd(amount,x)
  }

  // assets/main.js onlineSaleReceiptEvents / onlineSaleReceiptCost.
  // Read-only event reconstruction; never post cash or stock movements.
  val allPayments=pages(run,"payments")
  val allCash=pages(run,"cashTransactions")
  val onlineIds=mutableSetOf<String>()
  for(row in salesRows) checked("online:${row.id ?: row.ordinal}") {
   val sale=row.fields
   if(sale.optString("saleType","store")!="online") return@checked
   val id=sale.optString("id").ifBlank { row.id ?: error("Online sale without ID") }
   if(!onlineIds.add(id)) { issues+="Duplicate online sale ID: $id";return@checked }
   val total=n(sale,"total").max(BigDecimal.ZERO)
   if(total.signum()==0) return@checked
   val cost=sale.optJSONArray("items")?.let { items ->
    (0 until items.length()).fold(BigDecimal.ZERO) { sum,i ->
     val item=items.getJSONObject(i);sum+n(item,"cost")*n(item,"qty")
    }
   } ?: error("Missing sale items")
   val net=n(sale,"total")-n(sale,"tax")
   val date=sale.optString("date")
   val payments=allPayments.filter { p ->
    val x=p.fields
    x.optString("referenceType")=="sale" && x.optString("referenceId")==id &&
     n(x,"amount")>BigDecimal.ZERO && !x.optBoolean("returnAdjustment") && !x.optBoolean("nonCash")
   }
   val sameDay=payments.filter { it.fields.optString("date")==date }
    .fold(BigDecimal.ZERO) { z,p -> z+n(p.fields,"amount").max(BigDecimal.ZERO) }
   val openingCash=allCash.filter { c ->
    val x=c.fields
    x.optString("type")=="income" && x.optString("referenceType")=="sale" &&
     x.optString("referenceId")==id && x.optString("date")==date && n(x,"amount")>BigDecimal.ZERO
   }
   // Missing FX snapshots are explicit audit blockers, never silently use today's rate.
   val opening=if(openingCash.isNotEmpty()) {
    val cash=openingCash.fold(BigDecimal.ZERO) { z,c ->
     val x=c.fields
     val amountUsd=usd(n(x,"amount"),x)
     val invoiceUnit=sale.optString("currency","USD")
     val rate=LegacyFinancialExchange.usdPerUnit(invoiceUnit,
      sale.optJSONObject("rateSnapshot")?.optString(invoiceUnit))
     z+amountUsd.divide(rate,mc)
    }
    (cash-sameDay).max(BigDecimal.ZERO)
   } else {
    val corrected=listOf("paymentMetadataCorrectedAt","paymentCurrencyCorrectedAt",
     "financialFootprintRepairedAt").any { sale.optBoolean(it) || sale.optString(it).isNotBlank() }
    val subtract=if(corrected) payments.fold(BigDecimal.ZERO) { z,p -> z+n(p.fields,"amount").max(BigDecimal.ZERO) } else sameDay
    (n(sale,"paid")-subtract).max(BigDecimal.ZERO)
   }
   fun recognize(key:String,whenDate:String,amount:BigDecimal,discount:BigDecimal,snapshot:JSONObject?) {
    if(!inPeriod(whenDate) || amount<=BigDecimal.ZERO) return
    if(!seen.add(key)) { issues+="Duplicate historical receipt: $key";return }
    val ratio=amount.divide(total,mc).min(BigDecimal.ONE)
    val costRatio=(amount+discount).divide(total,mc).min(BigDecimal.ONE)
    val event=JSONObject(sale.toString())
    if(snapshot!=null) event.put("rateSnapshot",snapshot)
    online+=usd(net*ratio-cost*costRatio,event)
   }
   recognize("online-initial:$id",date,opening.min(total),BigDecimal.ZERO,sale.optJSONObject("rateSnapshot"))
   for(payment in payments) {
    val x=payment.fields
    val pid=x.optString("id").ifBlank { payment.id ?: "ordinal-${payment.ordinal}" }
    recognize("online-payment:$id:$pid",x.optString("date"),n(x,"amount"),
     n(x,"onlineCollectionDiscount").max(BigDecimal.ZERO),
     x.optJSONObject("rateSnapshot") ?: sale.optJSONObject("rateSnapshot"))
   }
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
   val total=if(record.isNull("laborPrice")) n(record,"price") else {
    val base=n(record,"laborPrice")+n(record,"partsTotal")
    base*(BigDecimal.ONE+n(record,"taxRate").max(BigDecimal.ZERO).divide(BigDecimal(100),mc))
   }
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
  return Result(store,repair,online,returns,expense,withdrawals,issues,onlineVerified=issues.none { it.startsWith("online:") || it.startsWith("Duplicate online") || it.startsWith("Duplicate historical receipt") })
 }
}
