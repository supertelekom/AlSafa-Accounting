package com.alsafa.native6

import org.json.JSONObject
import org.json.JSONArray

/** Audit only: historical receipts are evidence, never new cash postings. */
object LegacyPaymentAudit {
    fun inspect(store: Store): JSONObject {
        LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val ids = mutableSetOf<String>()
        val currencies = mutableMapOf<String, String>()
        val paidSnapshots = mutableMapOf<String, java.math.BigDecimal>()
        val paymentTotals = mutableMapOf<String, java.math.BigDecimal>()
        val invalidPaymentAmounts = JSONArray()
        val duplicateInvoiceIds = JSONArray()
        val currencyConflicts = JSONArray()
        for (section in listOf("sales", "purchases")) {
            for (row in store.legacyRows(section)) {
                val invoice = JSONObject(row.getValue("payload"))
                val key = section + ":" + invoice.optString("id")
                if (!ids.add(key)) duplicateInvoiceIds.put(key)
                currencies[key] = invoice.optString("currency")
                invoice.optString("paid").toBigDecimalOrNull()?.let { paidSnapshots[key] = it }
            }
        }
        val missing = JSONArray()
        val unsupportedReferenceTypes = JSONArray()
        val missingReferenceIds = JSONArray()
        val duplicatePaymentIds = JSONArray()
        val seenPaymentIds = mutableSetOf<String>()
        var linked = 0
        for (row in store.legacyRows("payments")) {
            val payment = JSONObject(row.getValue("payload"))
            val paymentId = payment.optString("id").trim()
            if (paymentId.isNotEmpty() && !seenPaymentIds.add(paymentId)) duplicatePaymentIds.put(paymentId)
            val section = when (payment.optString("referenceType").trim().lowercase()) {
                "sale", "sales" -> "sales"
                "purchase", "purchases" -> "purchases"
                else -> {
                    unsupportedReferenceTypes.put(JSONObject().put("archiveKey", row.getValue("legacy_id"))
                        .put("referenceType", payment.optString("referenceType")))
                    continue
                }
            }
            val referenceId = payment.optString("referenceId").trim()
            if (referenceId.isEmpty()) missingReferenceIds.put(row.getValue("legacy_id"))
            val key = section + ":" + referenceId
            if (key in ids) {
                linked++
                val amount = payment.optString("amount").toBigDecimalOrNull()
                if (amount == null || amount.signum() <= 0) {
                    invalidPaymentAmounts.put(row.getValue("legacy_id"))
                } else if (payment.optString("currency") != currencies[key]) {
                    currencyConflicts.put(row.getValue("legacy_id"))
                } else {
                    paymentTotals[key] = (paymentTotals[key] ?: java.math.BigDecimal.ZERO).add(amount)
                }
            } else missing.put(row.getValue("legacy_id"))
        }
        val differences = JSONArray()
        val snapshotsWithoutPaymentEvidence = JSONArray()
        for ((key, paid) in paidSnapshots) {
            if (paid.signum() > 0 && key !in paymentTotals) {
                snapshotsWithoutPaymentEvidence.put(JSONObject().put("invoice", key)
                    .put("paidSnapshot", paid.toPlainString()))
            }
        }
        for ((key, total) in paymentTotals) {
            val snapshot = paidSnapshots[key]
            if (snapshot == null || snapshot.compareTo(total) != 0) {
                differences.put(JSONObject().put("invoice", key)
                    .put("paidSnapshot", snapshot?.toPlainString() ?: "missing")
                    .put("paymentEvidenceTotal", total.toPlainString()))
            }
        }
        return JSONObject().put("linkedEvidenceCount", linked)
            .put("invalidPaymentAmounts", invalidPaymentAmounts)
            .put("unsupportedReferenceTypes", unsupportedReferenceTypes)
            .put("missingReferenceIds", missingReferenceIds)
            .put("duplicatePaymentIds", duplicatePaymentIds)
            .put("duplicateInvoiceIds", duplicateInvoiceIds)
            .put("requiresManualReconciliation", missing.length() > 0 ||
                invalidPaymentAmounts.length() > 0 || currencyConflicts.length() > 0 ||
                differences.length() > 0 || duplicatePaymentIds.length() > 0 ||
                duplicateInvoiceIds.length() > 0 || missingReferenceIds.length() > 0)
            .put("paymentCurrencyConflicts", currencyConflicts)
            .put("paymentSnapshotDifferences", differences)
            .put("paidSnapshotsWithoutEvidence", snapshotsWithoutPaymentEvidence)
            .put("evidenceDoesNotCreateCash", true)
            .put("unlinkedEvidenceKeys", missing)
            .put("readOnly", true).put("cashPosted", false)
    }
}
