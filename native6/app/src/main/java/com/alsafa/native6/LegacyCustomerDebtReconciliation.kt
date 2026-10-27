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
        val data = root.getJSONObject("data")
        val sales = data.getJSONArray("sales")
        val paymentsBySale = HashMap<String, BigDecimal>()
        val returnsBySale = HashMap<String, BigDecimal>()
        val payments = data.optJSONArray("payments") ?: JSONArray()
        for (i in 0 until payments.length()) {
            val payment = payments.optJSONObject(i) ?: continue
            if (payment.optString("referenceType") != "sale") continue
            val ref = payment.optString("referenceId")
            val amount = payment.optString("amount").toBigDecimalOrNull() ?: continue
            val currency = payment.optString("currency")
            val sale = (0 until sales.length()).asSequence().map { sales.getJSONObject(it) }
                .firstOrNull { it.optString("id") == ref } ?: continue
            if (sale.optString("currency") != currency || amount.signum() < 0) continue
            paymentsBySale[ref] = (paymentsBySale[ref] ?: BigDecimal.ZERO).add(amount)
        }
        val returns = data.optJSONArray("returns") ?: JSONArray()
        for (i in 0 until returns.length()) {
            val item = returns.optJSONObject(i) ?: continue
            if (item.optString("referenceType") != "sale") continue
            val ref = item.optString("referenceId")
            val amount = item.optString("debtReduction").toBigDecimalOrNull() ?: continue
            if (amount.signum() < 0) continue
            returnsBySale[ref] = (returnsBySale[ref] ?: BigDecimal.ZERO).add(amount)
        }
        val conflicts = JSONArray()
        val candidates = JSONArray()
        var settledConflicts = 0
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
            val ref = sale.optString("id")
            val explicitCollections = paymentsBySale[ref] ?: BigDecimal.ZERO
            val returnedCredit = returnsBySale[ref] ?: BigDecimal.ZERO
            val due = total.subtract(paid.max(explicitCollections)).subtract(returnedCredit)
            if (due.signum() <= 0) continue
            val status = sale.optString("paymentStatus").trim()
            val reason = when {
                status in settled && explicitCollections.signum() == 0 &&
                    returnedCredit.signum() == 0 -> "settled_status_without_matching_payments"
                status in settled -> "settled_status_with_unexplained_balance"
                else -> ""
            }
            if (reason.isNotEmpty()) settledConflicts++
            val record = JSONObject().put("index", i)
                .put("sourceId", sale.optString("id"))
                .put("invoiceNo", sale.optString("invoiceNo"))
                .put("status", status).put("currency", sale.optString("currency"))
                .put("remaining", due.toPlainString())
                .put("linkedPayments", explicitCollections.toPlainString())
                .put("returnedDebtReduction", returnedCredit.toPlainString())
            if (reason.isEmpty()) candidates.put(record)
            else conflicts.put(record.put("reason", reason))
        }
        return JSONObject().put("candidateCount", candidates.length())
            .put("conflictCount", conflicts.length())
            .put("settledStatusConflicts", settledConflicts)
            .put("invalidAmountConflicts", invalidAmounts)
            .put("candidates", candidates).put("conflicts", conflicts)
            .put("safeForAutomaticActivation", conflicts.length() == 0)
    }
}
