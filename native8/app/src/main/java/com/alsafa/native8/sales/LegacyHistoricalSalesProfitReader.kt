package com.alsafa.native8.sales

import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/** Read-only profit reconstruction from original invoice/payment JSON. */
object LegacyHistoricalSalesProfitReader {
 private val mc=MathContext.DECIMAL128
 data class Audit(val events:List<FinancialSourceReconciliation.Event>,val unresolved:List<String>)
 private fun amount(obj:JSONObject,key:String):BigDecimal =
  obj.optString(key).toBigDecimalOrNull() ?: error("Missing $key")
 private fun usd(value:BigDecimal,unit:String,raw:JSONObject):BigDecimal {
  val snapshot=raw.optJSONObject("rateSnapshot") ?: error("Missing FX snapshot")
  return value.multiply(LegacyFinancialExchange.usdPerUnit(unit,snapshot.optString(unit)),mc)
 }
 fun extract(invoices:List<LegacySaleEntity>,payments:List<JSONObject>):Audit {
  val events=mutableListOf<FinancialSourceReconciliation.Event>()
  val issues=mutableListOf<String>()
  val receipts=payments.filter {it.optString("referenceType")=="sale"}.groupBy {it.optString("referenceId")}
  val invoiceIds=invoices.map {it.legacy_id}.toSet()
  for(payment in payments.filter {it.optString("referenceType")=="sale"}) {
   if(payment.optString("referenceId") !in invoiceIds) issues+="Orphan payment: ${payment.optString("id")}"
  }
  for(invoice in invoices) {
   try {
    require(invoice.reconciliation=="confirmed") { "Invoice flagged for review" }
    val raw=JSONObject(invoice.original_json)
    val items=raw.getJSONArray("items")
    var cost=BigDecimal.ZERO
    for(i in 0 until items.length()) {
     val line=items.getJSONObject(i)
     cost+=amount(line,"qty").multiply(amount(line,"cost"),mc)
    }
    val total=amount(raw,"total")
    val tax=if(raw.has("tax")) amount(raw,"tax") else {
     require(!raw.has("taxRate") || raw.optString("taxRate") in setOf("0","0.0","")) { "Tax amount missing" }
     BigDecimal.ZERO
    }
    require(total>BigDecimal.ZERO && tax>=BigDecimal.ZERO && cost>=BigDecimal.ZERO)
    val unit=raw.optString("currency","USD")
    val channel=LegacySaleChannel.fromOriginalJson(invoice.original_json)
    if(channel==SalesChannelProfit.Channel.STORE) {
     events+=FinancialSourceReconciliation.Event(
      "archive:store:${invoice.source_sha256}:${invoice.legacy_id}",
      FinancialSourceReconciliation.Stream.LEGACY_STORE,
      LocalDate.parse(raw.getString("date")),usd(total-tax,unit,raw),usd(cost,unit,raw))
    } else {
     val paymentsForSale=receipts[invoice.legacy_id].orEmpty()
     var collected=BigDecimal.ZERO
     for(payment in paymentsForSale) {
      val id=payment.getString("id")
      val paid=amount(payment,"amount")
      val discount=payment.optString("onlineCollectionDiscount","0").toBigDecimal()
      require(paid>=BigDecimal.ZERO && discount>=BigDecimal.ZERO)
      val paymentUnit=payment.optString("currency","USD")
      val paidInInvoiceCurrency=if(paymentUnit==unit) paid else {
       val base=usd(paid,paymentUnit,payment)
       base.divide(LegacyFinancialExchange.usdPerUnit(unit,
        payment.getJSONObject("rateSnapshot").optString(unit)),mc)
      }
      require(collected+paidInInvoiceCurrency+discount<=total+BigDecimal("0.01")) {
       "Online collections exceed invoice total"
      }
      collected+=paidInInvoiceCurrency+discount
      val ratio=paidInInvoiceCurrency.divide(total,mc)
      val costRatio=(paidInInvoiceCurrency+discount).divide(total,mc)
      val recognizedRevenue=usd(total-tax,unit,raw).multiply(ratio,mc)
      val recognizedCost=usd(cost,unit,raw).multiply(costRatio,mc)
      events+=FinancialSourceReconciliation.Event(
       "archive:online:${invoice.source_sha256}:$id",
       FinancialSourceReconciliation.Stream.LEGACY_ONLINE,
       LocalDate.parse(payment.getString("date")),recognizedRevenue,recognizedCost)
     }
     if(paymentsForSale.isEmpty() && raw.optString("onlineStatus")=="تم التحصيل")
      issues+="Online collection has no dated payment: ${invoice.legacy_id}"
    }
   } catch(e:Exception) {
    issues+="Invoice ${invoice.legacy_id}: ${e.message}"
   }
  }
  require(events.map {it.sourceKey}.distinct().size==events.size) { "Duplicate source financial event" }
  return Audit(events,issues)
 }
}
