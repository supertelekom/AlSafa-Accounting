package com.alsafa.native8.sales

import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/** main.js onlineSaleReceiptEvents/grossProfitUsdForRange; no stock or cash writes. */
object LegacyHistoricalSalesProfitReader {
 private val mc=MathContext.DECIMAL128
 private val zero=BigDecimal.ZERO
 private val one=BigDecimal.ONE
 data class Audit(val events:List<FinancialSourceReconciliation.Event>,val unresolved:List<String>)
 private fun amount(obj:JSONObject,key:String):BigDecimal =
  obj.optString(key).toBigDecimalOrNull() ?: error("Missing or invalid $key")
 private fun optional(obj:JSONObject,key:String):BigDecimal =
  if(obj.isNull(key)) zero else amount(obj,key)
 private fun id(obj:JSONObject)=obj.getString("id").also { require(it.isNotBlank()) { "Missing source ID" } }
 private fun usd(value:BigDecimal,unit:String,snapshot:JSONObject?):BigDecimal =
  value.multiply(LegacyFinancialExchange.usdPerUnit(unit,snapshot?.optString(unit)),mc)
 private fun convert(value:BigDecimal,from:String,to:String,snapshot:JSONObject?):BigDecimal {
  if(from==to) return value
  val direct=snapshot?.optString("TRY_SYP")?.toBigDecimalOrNull()
  if(direct!=null && direct>zero) {
   if(from=="TRY" && to=="SYP") return value.multiply(direct,mc)
   if(from=="SYP" && to=="TRY") return value.divide(direct,mc)
  }
  return usd(value,from,snapshot).divide(LegacyFinancialExchange.usdPerUnit(to,snapshot?.optString(to)),mc)
 }
 fun extract(invoices:List<LegacySaleEntity>,payments:List<JSONObject>,
  cash:List<JSONObject> = emptyList(),returns:List<JSONObject> = emptyList()):Audit =
  extractOriginal(invoices.map { JSONObject(it.original_json) },payments,cash,returns)

 fun extractOriginal(invoices:List<JSONObject>,payments:List<JSONObject>,
  cash:List<JSONObject>,returns:List<JSONObject>):Audit {
  val events=mutableListOf<FinancialSourceReconciliation.Event>()
  val issues=mutableListOf<String>()
  val salePayments=payments.filter { it.optString("referenceType")=="sale" }
  val saleCash=cash.filter { it.optString("referenceType")=="sale" }
  val saleReturns=returns.filter { it.optString("referenceType")=="sale" }
  // Reject all copies of ambiguous IDs, including their dependent events.
  fun duplicates(rows:List<JSONObject>)=rows.groupBy { it.optString("id") }
   .filter { (key,group) -> key.isBlank() || group.size>1 }.keys
  val badSales=duplicates(invoices)
  val badPayments=duplicates(salePayments)
  val badCash=duplicates(saleCash)
  val badReturns=duplicates(saleReturns)
  val invoiceIds=invoices.map { it.optString("id") }.toSet()
  for(row in salePayments+saleReturns) if(row.optString("referenceId") !in invoiceIds)
   issues+="Orphan source record: ${row.optString("id")}"
  val rejected=mutableSetOf<String>()
  for(raw in invoices) {
   val saleId=raw.optString("id")
   try {
    require(saleId !in badSales) { "Duplicate or missing sale ID" }
    id(raw)
    val pending=mutableListOf<FinancialSourceReconciliation.Event>()
    val items=raw.getJSONArray("items")
    require(items.length()>0) { "Missing original item costs" }
    var cost=zero
    for(i in 0 until items.length()) {
     val line=items.getJSONObject(i)
     val qty=amount(line,"qty");val unitCost=amount(line,"cost")
     require(qty>=zero && unitCost>=zero) { "Invalid item quantity/cost" }
     cost+=qty.multiply(unitCost,mc)
    }
    val total=amount(raw,"total").max(zero)
    val tax=if(raw.isNull("tax")) {
     require(optional(raw,"taxRate").signum()==0) { "Tax amount missing" };zero
    } else amount(raw,"tax").max(zero)
    val net=(total-tax).max(zero)
    val unit=raw.getString("currency")
    val snapshot=raw.optJSONObject("rateSnapshot")
    val date=LocalDate.parse(raw.getString("date"))
    val online=LegacySaleChannel.isOnline(raw.toString())
    if(!online) {
     pending+=FinancialSourceReconciliation.Event("legacy:store:$saleId",
      FinancialSourceReconciliation.Stream.LEGACY_STORE,date,usd(net,unit,snapshot),usd(cost,unit,snapshot))
    } else if(total>zero) {
     val linked=salePayments.filter { it.optString("referenceId")==saleId }
     require(linked.none { it.optString("id") in badPayments }) { "Duplicate or missing payment ID" }
     val receipts=linked.filter { amount(it,"amount")>zero && !it.optBoolean("returnAdjustment") && !it.optBoolean("nonCash") }
     val sameDay=receipts.filter { it.optString("date")==date.toString() }.fold(zero) { sum,p -> sum+amount(p,"amount") }
     val openingCash=saleCash.filter { it.optString("type")=="income" && it.optString("referenceId")==saleId &&
      it.optString("date")==date.toString() && amount(it,"amount")>zero }
     require(openingCash.none { it.optString("id") in badCash }) { "Duplicate or missing cash ID" }
     val opening=if(openingCash.isNotEmpty()) {
      openingCash.fold(zero) { sum,row -> sum+convert(amount(row,"amount"),row.getString("currency"),unit,
       row.optJSONObject("rateSnapshot") ?: snapshot).max(zero) }-sameDay
     } else {
      val corrected=listOf("paymentMetadataCorrectedAt","paymentCurrencyCorrectedAt","financialFootprintRepairedAt")
       .any { !raw.isNull(it) && raw.optString(it) !in setOf("","false","0") }
      amount(raw,"paid")-if(corrected) receipts.fold(zero) { sum,p -> sum+amount(p,"amount") } else sameDay
     }
     fun receipt(key:String,on:String,paid:BigDecimal,discount:BigDecimal,fx:JSONObject?) {
      if(paid<=zero) return
      val ratio=paid.divide(total,mc).coerceIn(zero,one)
      val costRatio=(paid+discount.max(zero)).divide(total,mc).coerceIn(zero,one)
      pending+=FinancialSourceReconciliation.Event(key,FinancialSourceReconciliation.Stream.LEGACY_ONLINE,
       LocalDate.parse(on),usd(net.multiply(ratio,mc),unit,fx),usd(cost.multiply(costRatio,mc),unit,fx))
     }
     receipt("legacy:online:$saleId:initial",date.toString(),opening.coerceIn(zero,total),zero,snapshot)
     for(payment in receipts) {
      // amount is already in invoice currency; paymentAmount/paymentCurrency are cash evidence only.
      require(payment.optString("currency",unit)==unit) { "Payment amount currency differs from invoice" }
      receipt("legacy:online:$saleId:payment:${id(payment)}",payment.getString("date"),amount(payment,"amount"),
       optional(payment,"onlineCollectionDiscount"),payment.optJSONObject("rateSnapshot") ?: snapshot)
     }
    }
    // Publish a whole invoice only after every dependent receipt was validated.
    events.addAll(pending)
   } catch(e:Exception) {
    rejected+=saleId
    issues+="Invoice $saleId: ${e.message}"
   }
  }
  for(row in saleReturns) try {
   val returnId=id(row)
   require(returnId !in badReturns) { "Duplicate return ID" }
   require(row.optString("referenceId") in invoiceIds && row.optString("referenceId") !in rejected) { "Unresolved original sale" }
   val credit=if(row.isNull("netCreditAmount")) amount(row,"creditAmount") else amount(row,"netCreditAmount")
   val ratio=if(row.isNull("recognizedRatio")) one else amount(row,"recognizedRatio").coerceIn(zero,one)
   val delta=(amount(row,"returnedCost")-amount(row,"replacementCost")-credit).multiply(ratio,mc)
   events+=FinancialSourceReconciliation.Event("legacy:sale-return:$returnId",FinancialSourceReconciliation.Stream.RETURN,
    LocalDate.parse(row.getString("date")),zero,zero,usd(delta,row.getString("currency"),row.optJSONObject("rateSnapshot")))
  } catch(e:Exception) { issues+="Return ${row.optString("id")}: ${e.message}" }
  check(events.map {it.sourceKey}.distinct().size==events.size) { "Duplicate source financial event" }
  return Audit(events,issues.distinct())
 }
}
