package com.alsafa.native8.sales

import com.alsafa.native8.data.migration.LegacyStagingDatabase
import org.json.JSONObject
import java.math.BigDecimal

/** Historical collections are read for reconciliation only; never posted into today's cashboxes. */
class LegacyPaymentReconciler(private val staging: LegacyStagingDatabase) {
 fun amounts(runId: Long): Map<String,BigDecimal> {
  val result=mutableMapOf<String,BigDecimal>()
  staging.readableDatabase.rawQuery(
   "SELECT json FROM legacy_records WHERE run_id=? AND section='payments'",
   arrayOf(runId.toString())
  ).use { cursor ->
   while(cursor.moveToNext()) {
    val payment=JSONObject(cursor.getString(0))
    if(payment.optString("referenceType")!="sale") continue
    val id=payment.optString("referenceId")
    val amount=payment.optString("amount").toBigDecimalOrNull() ?: continue
    if(id.isNotBlank() && amount>=BigDecimal.ZERO) result[id]=(result[id] ?: BigDecimal.ZERO)+amount
   }
  }
  return result
 }
}
