package com.alsafa.native8.sales

import com.alsafa.native8.data.migration.LegacyReadRepository
import org.json.JSONObject

/** One import-run read-only source projection; no ledger or inventory writes. */
class LegacyHistoricalSalesProfitRepository(
 private val archive:LegacySaleDao,
 private val legacy:LegacyReadRepository
) {
 private fun sameJson(a:Any?,b:Any?):Boolean = when {
  a is JSONObject && b is JSONObject -> {
   val keys=a.keys().asSequence().toSet()
   keys==b.keys().asSequence().toSet() && keys.all { sameJson(a.get(it),b.get(it)) }
  }
  a is org.json.JSONArray && b is org.json.JSONArray ->
   a.length()==b.length() && (0 until a.length()).all { sameJson(a.get(it),b.get(it)) }
  a is Number && b is Number -> a.toString().toBigDecimal().compareTo(b.toString().toBigDecimal())==0
  else -> a==b
 }
 private fun rows(run:Long,section:String):List<JSONObject> {
  val result=mutableListOf<JSONObject>()
  var offset=0
  while(true) {
   val page=legacy.page(run,section,offset,500)
   result.addAll(page.rows.map {it.fields})
   offset+=page.rows.size
   if(offset>=page.total) return result
   check(page.rows.isNotEmpty()) { "Incomplete historical payments" }
  }
 }
 suspend fun inspect():LegacyHistoricalSalesProfitReader.Audit {
  val run=legacy.latestRunId() ?: return LegacyHistoricalSalesProfitReader.Audit(emptyList(),
   listOf("No imported source run"))
  val invoices=archive.historicalInPeriod(Long.MIN_VALUE,Long.MAX_VALUE)
  val staged=rows(run,"sales")
  val byId=staged.groupBy { it.optString("id") }
  if(byId.size!=invoices.size || invoices.any { invoice ->
    val originals=byId[invoice.legacy_id].orEmpty()
    originals.size!=1 || !sameJson(originals.single(),JSONObject(invoice.original_json))
   }) return LegacyHistoricalSalesProfitReader.Audit(emptyList(),listOf("Archive and latest source run differ"))
  val result=LegacyHistoricalSalesProfitReader.extract(invoices,rows(run,"payments"),rows(run,"cashTransactions"),rows(run,"returns"))
  check(legacy.latestRunId()==run) { "Historical source changed during read" }
  return result
 }
}
