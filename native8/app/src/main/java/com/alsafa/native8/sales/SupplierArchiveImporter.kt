package com.alsafa.native8.sales

import androidx.room.withTransaction
import com.alsafa.native8.data.migration.LegacyReadRepository
import com.alsafa.native8.data.migration.LegacySupplierReconciliation
import com.alsafa.native8.data.migration.LegacyStagingDatabase

/**
 * Historical supplier import. No cash, stock or payable posting.
 * Entire source imported atomically; repeated source is verified by counts.
 */
class SupplierArchiveImporter(
 private val staging:LegacyStagingDatabase,
 private val room:AppDatabase
) {
 data class Result(val suppliers:Int,val purchases:Int,val ledger:Int,val alreadyImported:Boolean)
 suspend fun importRun(runId:Long):Result {
  val source=LegacyReadRepository(staging)
  val audit=LegacySupplierReconciliation(source).inspect(runId)
  require(audit.issues.isEmpty()) { "Supplier archive audit failed: ${audit.issues.take(5)}" }
  val sha=staging.readableDatabase.rawQuery(
   "SELECT source_sha256 FROM import_runs WHERE id=?",arrayOf(runId.toString())
  ).use { c -> check(c.moveToFirst());c.getString(0) }
  fun all(section:String):List<LegacyReadRepository.LegacyRow> {
   val result=mutableListOf<LegacyReadRepository.LegacyRow>()
   var offset=0
   while(true) {
    val page=source.page(runId,section,offset,500)
    result.addAll(page.rows)
    offset+=page.rows.size
    if(offset>=page.total) break
    check(page.rows.isNotEmpty())
   }
   return result
  }
  val suppliers=all("suppliers")
  val purchases=all("purchases")
  val ledger=all("supplierLedger")
  val dao=room.supplierPurchaseDao()
  return room.withTransaction {
   val count=dao.purchaseCount(sha)
   if(count>0) {
    require(count==purchases.size) { "Incomplete previous supplier archive import" }
    Result(suppliers.size,purchases.size,ledger.size,true)
   } else {
    val now=System.currentTimeMillis()
    suppliers.forEach { row ->
     val id=requireNotNull(row.id) { "Missing supplier ID" }
     val obj=row.fields
     dao.insertSupplier(SupplierArchiveEntity("$sha:$id",sha,id,id,
      obj.optString("name"),obj.toString(),now))
    }
    purchases.forEach { row ->
     val id=requireNotNull(row.id) { "Missing purchase ID" }
     val obj=row.fields
     dao.insertPurchase(PurchaseArchiveEntity("$sha:$id",sha,id,
      obj.optString("supplierId"),obj.optString("currency"),
      obj.optString("invoiceNo").ifBlank { obj.optString("number") },
      obj.optString("date"),obj.optString("total"),obj.optString("paid","0"),
      obj.toString(),now))
    }
    ledger.forEach { row ->
     val id=requireNotNull(row.id) { "Missing supplier ledger ID" }
     val obj=row.fields
     dao.insertLedger(SupplierLedgerArchiveEntity("$sha:$id",sha,id,
      obj.optString("supplierId"),obj.optString("currency"),obj.optString("kind"),
      obj.optString("amount"),obj.optString("referenceId")
       .ifBlank { obj.optString("invoiceId") }.takeIf { it.isNotBlank() },
      obj.toString(),now))
    }
    Result(suppliers.size,purchases.size,ledger.size,false)
   }
  }
 }
}
