package com.alsafa.native8.sales

import androidx.room.withTransaction
import kotlinx.coroutines.runBlocking
import com.alsafa.native8.data.migration.LegacyStagingDatabase
import org.json.JSONObject
import java.math.BigDecimal

class LegacySalesArchiveImporter(
 private val staging: LegacyStagingDatabase,
 private val operational: AppDatabase
) {
 data class Result(val count: Int,val review: Int,val alreadyImported: Boolean)
 fun importRun(runId: Long): Result {
  val source=staging.readableDatabase.rawQuery("SELECT source_sha256 FROM import_runs WHERE id=?",arrayOf(runId.toString())).use {
   require(it.moveToFirst());it.getString(0)
  }
  val historicalPayments=LegacyPaymentReconciler(staging).amounts(runId)
  return runBlocking {
   operational.withTransaction {
    val dao=operational.legacySaleDao()
    val existing=dao.importForSource(source)
    if(existing!=null) return@withTransaction Result(existing.invoice_count,existing.review_count,true)
    var count=0
    var review=0
    staging.readableDatabase.rawQuery("SELECT json FROM legacy_records WHERE run_id=? AND section='sales' ORDER BY ordinal",arrayOf(runId.toString())).use { rows ->
     while(rows.moveToNext()) {
      val raw=rows.getString(0)
      val sale=JSONObject(raw)
      val id=sale.getString("id")
      val number=sale.getLong("invoiceNo")
      val total=sale.getString("total").toBigDecimal()
      val paid=sale.getString("paid").toBigDecimal().max(historicalPayments[id] ?: BigDecimal.ZERO)
      require(number>0 && total>=BigDecimal.ZERO && paid>=BigDecimal.ZERO)
      val status=sale.optString("paymentStatus")
      val safe=paid<=total && !status.contains("مسترجع") && !status.contains("مسوّاة") &&
       !(status.contains("مدفوع بالكامل") && paid<total)
      val due=if(safe && (status.contains("جزئي") || status.contains("آجل"))) total-paid else BigDecimal.ZERO
      dao.insertLegacy(LegacySaleEntity(id,source,number,sale.getLong("createdAt"),
       sale.optString("customer"),sale.optString("phone"),sale.optString("currency","USD"),
       total.toPlainString(),paid.toPlainString(),due.toPlainString(),
       if(safe)"confirmed" else "review",status,raw))
      val items=sale.getJSONArray("items")
      for(i in 0 until items.length())
       dao.insertLegacyLine(RoomLegacySaleLine(id+"#"+i,id,i,items.getJSONObject(i).toString()))
      if(due>BigDecimal.ZERO)
       operational.legacyReceivablesDao().insert(RoomReceivable(id,sale.optString("customer"),
        sale.optString("phone"),due.toPlainString()))
      count++
      if(!safe) review++
     }
    }
    require(count>0)
    dao.insertLegacyImport(RoomLegacySaleImport(source,System.currentTimeMillis(),count,review))
    Result(count,review,false)
   }
  }
 }
}
