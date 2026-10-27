package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Reconciles legacy display status against monetary fields BEFORE cutover.
 * Discrepancies must be reviewed against payments/returns; this code does not
 * silently interpret stale "paid" fields as collectible customer debts.
 */
object LegacyCustomerDebtReconciliation {
    private val settled = setOf("مدفوع بالكامل", "مسوّاة/مسترجعة", "ملغاة", "مسترجعة")

    fun inspect(root: JSONObject): JSONObject {
        require(root.optString("format") == "alsafa-accounting-backup" &&
            root.optInt("formatVersion") == 2)
        val sales = root.getJSONObject("data").getJSONArray("sales")
        val conflicts = JSONArray()
        val candidates = JSONArray()
        var settledConflicts = 0
        var missingCustomer = 0
        var invalidAmounts = 0
        for (i in 0 until sales.length()) {
            val sale = sales.getJSONObject(i)
            val total = sale.optString("total").toBigDecimalOrNull()
            val paid = sale.optString("paid").toBigDecimalOrNull()
            if (total == null || paid == null || total.signum() < 0 ||
                paid.signum() < 0 || paid > total) {
                invalidAmounts++
                conflicts.put(JSONObject().put("index", i)
                    .put("sourceId", sale.optString("id")).put("reason", "invalid_amounts"))
                continue
            }
            val due = total.subtract(paid)
            if (due.signum() == 0) continue
            val status = sale.optString("paymentStatus").trim()
            val customer = sale.optString("customer").trim()
            val reason = when {
                status in settled -> {
                    settledConflicts++
                    "settled_status_with_positive_due"
                }
                customer.isEmpty() -> {
                    missingCustomer++
                    "missing_customer_for_open_balance"
                }
                else -> ""
            }
            val record = JSONObject().put("index", i)
                .put("sourceId", sale.optString("id"))
                .put("invoiceNo", sale.optString("invoiceNo"))
                .put("status", status).put("currency", sale.optString("currency"))
                .put("remaining", due.toPlainString())
            if (reason.isEmpty()) candidates.put(record)
            else conflicts.put(record.put("reason", reason))
        }
        return JSONObject().put("candidateCount", candidates.length())
            .put("conflictCount", conflicts.length())
            .put("settledStatusConflicts", settledConflicts)
            .put("missingCustomerConflicts", missingCustomer)
            .put("invalidAmountConflicts", invalidAmounts)
            .put("candidates", candidates).put("conflicts", conflicts)
            .put("safeForAutomaticActivation", conflicts.length() == 0)
    }
}
