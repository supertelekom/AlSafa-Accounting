package com.alsafa.native8.sales

import android.content.ContentValues
import com.alsafa.native8.data.migration.LegacyStagingDatabase
import com.alsafa.native8.data.operational.OperationalDatabase
import org.json.JSONObject
import java.math.BigDecimal

class LegacySalesArchiveImporter(
 private val staging: LegacyStagingDatabase,
 private val operational: OperationalDatabase
) {
 data class Result(val count: Int,val review: Int,val alreadyImported: Boolean)
 fun importRun(runId: Long): Result {
  val source=staging.readableDatabase.rawQuery("SELECT source_sha256 FROM import_runs WHERE id=?",arrayOf(runId.toString())).use {
   require(it.moveToFirst());it.getString(0)
  }
  val db=operational.writableDatabase
  db.beginTransaction()
  try {
   LegacySalesArchiveSchema.ensure(db)
   db.rawQuery("SELECT invoice_count,review_count FROM legacy_sales_imports WHERE source_sha256=?",arrayOf(source)).use {
    if(it.moveToFirst()) {val result=Result(it.getInt(0),it.getInt(1),true);db.setTransactionSuccessful();return result}
   }
   var count=0
   var review=0
   staging.readableDatabase.rawQuery("SELECT json FROM legacy_records WHERE run_id=? AND section='sales' ORDER BY ordinal",arrayOf(runId.toString())).use { rows ->
    while(rows.moveToNext()) {
     val raw=rows.getString(0)
     val sale=JSONObject(raw)
     val id=sale.getString("id")
     val number=sale.getLong("invoiceNo")
     val total=sale.getString("total").toBigDecimal()
     val paid=sale.getString("paid").toBigDecimal()
     require(number>0 && total>=BigDecimal.ZERO && paid>=BigDecimal.ZERO)
     val status=sale.optString("paymentStatus")
     val safe=paid<=total && !status.contains("مسترجع") && !status.contains("مسوّاة") &&
      !(status.contains("مدفوع بالكامل") && paid<total)
     val due=if(safe && (status.contains("جزئي") || status.contains("آجل"))) total-paid else BigDecimal.ZERO
     db.insertOrThrow("legacy_sales_archive",null,ContentValues().apply {
      put("legacy_id",id);put("source_sha256",source);put("invoice_number",number)
      put("created_at",sale.getLong("createdAt"));put("customer",sale.optString("customer"))
      put("phone",sale.optString("phone"));put("currency",sale.optString("currency","USD"))
      put("total",total.toPlainString());put("historical_paid",paid.toPlainString())
      put("due",due.toPlainString());put("reconciliation",if(safe)"confirmed" else "review")
      put("status",status);put("original_json",raw)
     })
     val items=sale.getJSONArray("items")
     for(i in 0 until items.length()) db.insertOrThrow("legacy_sales_lines",null,ContentValues().apply {
      put("id",id+"#"+i);put("sale_id",id);put("ordinal",i)
      put("original_json",items.getJSONObject(i).toString())
     })
     if(due>BigDecimal.ZERO) db.insertOrThrow("customer_receivables",null,ContentValues().apply {
      put("sale_id",id);put("customer",sale.optString("customer"))
      put("phone",sale.optString("phone"));put("balance",due.toPlainString())
     })
     count++
     if(!safe) review++
    }
   }
   require(count>0)
   db.insertOrThrow("legacy_sales_imports",null,ContentValues().apply {
    put("source_sha256",source);put("imported_at",System.currentTimeMillis())
    put("invoice_count",count);put("review_count",review)
   })
   db.setTransactionSuccessful()
   return Result(count,review,false)
  } finally {db.endTransaction()}
 }
}
