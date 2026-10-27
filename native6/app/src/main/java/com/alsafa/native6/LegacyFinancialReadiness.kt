package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject

/**
 * Preflight only. Historical money must never be posted by a report.
 * This report is deliberately not a migration receipt.
 */
object LegacyFinancialReadiness {
    fun inspect(store: Store): JSONObject {
        val gate = LegacyFinancialActivationGate.inspect(store)
        val blockers = JSONArray()
        val gateReasons = gate.optJSONArray("blockingReasons")
        if (gateReasons != null) for (i in 0 until gateReasons.length()) {
            blockers.put(gateReasons.getString(i))
        }
        val linkage = runCatching { LegacyFinancialLinkage.inspect(store) }
        if (linkage.isFailure) {
            blockers.put("تعذر فحص روابط المدفوعات والمرتجعات: " +
                (linkage.exceptionOrNull()?.message ?: "خطأ غير معروف"))
        } else {
            val result = linkage.getOrThrow()
            for (field in listOf("missingReferences", "unresolvedReferences",
                "duplicateInvoiceIds", "duplicateMovementIds")) {
                val count = result.optJSONArray(field)?.length() ?: 0
                if (count > 0) {
                    val label = when(field) {
                        "missingReferences" -> "حركات بلا رقم فاتورة"
                        "unresolvedReferences" -> "حركات لا تطابق فاتورة موجودة"
                        "duplicateInvoiceIds" -> "معرفات فواتير مكررة"
                        else -> "معرفات حركات مكررة"
                    }
                    blockers.put("$label: $count")
                }
            }
        }
        val eligible = gate.optBoolean("financialActive", false) &&
            linkage.getOrNull()?.optBoolean("complete", false) == true && blockers.length() == 0
        return JSONObject()
            .put("preflightPassed", eligible)
            .put("canActivate", false)
            .put("requiresAtomicPosting", true)
            .put("archiveIsOperationalLedger", false)
            .put("blockers", blockers)
            .put("linkage", linkage.getOrNull() ?: JSONObject())
            .put("gate", gate)
            .put("posting", "NONE")
    }
}
