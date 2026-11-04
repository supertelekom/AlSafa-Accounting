package com.alsafa.native8.maintenance

import androidx.room.withTransaction
import com.alsafa.native8.data.migration.LegacyStagingDatabase
import com.alsafa.native8.sales.AppDatabase
import org.json.JSONObject

/** Import only original repair/part records; do not re-post payments, stock or profit. */
class MaintenanceLegacyImporter(
 private val staging:LegacyStagingDatabase,
 private val operational:AppDatabase
) {
 data class Result(val repairs:Int,val parts:Int)
 suspend fun importRun(runId:Long):Result {
  val source=staging.readableDatabase.rawQuery(
   "SELECT source_sha256 FROM import_runs WHERE id=?",arrayOf(runId.toString())
  ).use { cursor -> require(cursor.moveToFirst());cursor.getString(0) }
  fun records(section:String):List<Pair<Int,JSONObject>> =
   staging.readableDatabase.rawQuery(
    "SELECT ordinal,json FROM legacy_records WHERE run_id=? AND section=? ORDER BY ordinal",
    arrayOf(runId.toString(),section)
   ).use { cursor -> buildList {
    while(cursor.moveToNext()) add(cursor.getInt(0) to JSONObject(cursor.getString(1)))
   } }
  val repairs=records("repairs").map { (ordinal,j) ->
   MaintenanceRepairEntity(
    j.optString("id").ifBlank { source+"#repair#"+ordinal },source,
    j.optLong("invoiceNo"),j.optString("date"),j.optString("time"),
    j.optString("status"),j.optString("customer"),j.optString("phone"),
    j.optString("watch"),j.optString("model"),j.optString("problem"),
    j.optString("currency","USD"),j.optString("price"),j.optString("received"),
    j.optString("deliveredDate"),j.optLong("createdAt"),j.toString())
  }
  val parts=records("maintenanceParts").map { (ordinal,j) ->
   MaintenancePartEntity(
    j.optString("id").ifBlank { source+"#part#"+ordinal },source,
    j.optString("name"),j.optString("category"),j.optString("barcode"),
    j.optString("stock"),j.optString("currency","USD"),
    j.optString("cost"),j.optString("salePrice"),j.optLong("createdAt"),j.toString())
  }
  return operational.withTransaction {
   val dao=operational.maintenanceDao()
   Result(dao.insertRepairs(repairs).count { it!=-1L },
    dao.insertParts(parts).count { it!=-1L })
  }
 }
}
