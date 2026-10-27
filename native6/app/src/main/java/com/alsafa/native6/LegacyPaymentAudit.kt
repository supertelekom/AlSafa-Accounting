package com.alsafa.native6

import org.json.JSONObject
import org.json.JSONArray

/** Audit only: historical receipts are evidence, never new cash postings. */
object LegacyPaymentAudit {
    fun inspect(store: Store): JSONObject {
        LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val ids = mutableSetOf<String>()
        for (section in listOf("sales", "purchases")) {
            for (row in store.legacyRows(section)) {
                val invoice = JSONObject(row.getValue("payload"))
                ids.add(section + ":" + invoice.optString("id"))
            }
        }
        val missing = JSONArray()
        var linked = 0
        for (row in store.legacyRows("payments")) {
            val payment = JSONObject(row.getValue("payload"))
            val section = when (payment.optString("referenceType").lowercase()) {
                "sale", "sales" -> "sales"
                "purchase", "purchases" -> "purchases"
                else -> continue
            }
            val key = section + ":" + payment.optString("referenceId")
            if (key in ids) linked++ else missing.put(row.getValue("legacy_id"))
        }
        return JSONObject().put("linkedEvidenceCount", linked)
            .put("unlinkedEvidenceKeys", missing)
            .put("readOnly", true).put("cashPosted", false)
    }
}
