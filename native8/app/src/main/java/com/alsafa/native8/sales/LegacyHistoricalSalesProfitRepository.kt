package com.alsafa.native8.sales

import com.alsafa.native8.data.migration.LegacyReadRepository
import org.json.JSONObject

/** One import-run read-only source projection; no ledger or inventory writes. */
class LegacyHistoricalSalesProfitRepository(
 private val archive:LegacySaleDao,
 private val legacy:LegacyReadRepository
) {
 private fun paymentRows(run:Long):List<JSONObject> {
  val result=mutableListOf<JSONObject>()
  var offset=0
  while(true) {
   val page=legacy.page(run,"payments",offset,500)
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
  return LegacyHistoricalSalesProfitReader.extract(invoices,paymentRows(run))
 }
}
