package com.alsafa.native8.sales

import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/**
 * Read-only extractor for source-confirmed archived STORE invoices.
 * Unclassified and ONLINE invoices require separate source payment parity.
 * No cash, inventory or historical data writes.
 */
object LegacySalesEventExtractor {
 data class Result(val events:List<FinancialSourceReconciliation.Event>,
  val unresolved:List<String>)
 private val mc=MathContext.DECIMAL128
 private fun number(obj:JSONObject,key:String):BigDecimal =
  obj.optString(key).toBigDecimalOrNull() ?: error("Missing $key")
 fun extractStore(invoices:List<LegacySaleEntity>):Result {
  val events=mutableListOf<FinancialSourceReconciliation.Event>()
  val unresolved=mutableListOf<String>()
  val seen=mutableSetOf<String>()
  for(invoice in invoices) {
   val key="legacy:store:${invoice.source_sha256}:${invoice.legacy_id}"
   if(!seen.add(key)) { unresolved+=key;continue }
   try {
    val raw=JSONObject(invoice.original_json)
    require(raw.optString("saleType")=="store") { "Unverified saleType" }
    require(invoice.reconciliation=="confirmed") { "Invoice needs reconciliation" }
    val day=LocalDate.parse(raw.getString("date"))
    val currency=raw.getString("currency").uppercase()
    val snapshot=raw.getJSONObject("rateSnapshot")
    val usdPerUnit=LegacyFinancialExchange.usdPerUnit(currency,
     if(currency=="USD") null else snapshot.getString(currency))
    val lines=raw.getJSONArray("items")
    require(lines.length()>0)
    var costUsd=BigDecimal.ZERO
    for(i in 0 until lines.length()) {
     val item=lines.getJSONObject(i)
     val quantity=number(item,"qty")
     require(quantity>BigDecimal.ZERO)
     val allocations=item.getJSONArray("fundingAllocations")
     require(allocations.length()>0) { "No historical cost allocation" }
     var allocated=BigDecimal.ZERO
     for(j in 0 until allocations.length()) {
      val lot=allocations.getJSONObject(j)
      val qty=number(lot,"qty")
      val unitCost=number(lot,"unitCost")
      val lotCurrency=lot.getString("currency").uppercase()
      require(qty>BigDecimal.ZERO && unitCost>=BigDecimal.ZERO)
      val lotRate=LegacyFinancialExchange.usdPerUnit(lotCurrency,
       if(lotCurrency=="USD") null else snapshot.getString(lotCurrency))
      allocated+=qty
      costUsd+=qty.multiply(unitCost,mc).multiply(lotRate,mc)
     }
     require(allocated.compareTo(quantity)==0) { "Funding quantity mismatch" }
    }
    val total=number(raw,"total")
    val tax=number(raw,"tax")
    require(total>=tax && tax>=BigDecimal.ZERO)
    events+=FinancialSourceReconciliation.Event(key,
     FinancialSourceReconciliation.Stream.LEGACY_STORE,day,
     (total-tax).multiply(usdPerUnit,mc),costUsd)
   } catch(_:Exception) { unresolved+=key }
  }
  return Result(events,unresolved)
 }
}
