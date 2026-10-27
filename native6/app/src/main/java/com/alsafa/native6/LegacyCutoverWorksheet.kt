package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Cutover worksheet derived from the original backup, not from replaying old
 * transactions. It preserves separate currencies and never posts to the ledger.
 * A future activation step must reconcile these totals with native openings.
 */
object LegacyCutoverWorksheet {
    private val currencies = setOf("USD", "TRY", "SYP")

    fun inspect(root: JSONObject): JSONObject {
        require(root.optString("format") == "alsafa-accounting-backup" &&
            root.optInt("formatVersion") == 2) { "صيغة النسخة غير مدعومة" }
        val data = root.getJSONObject("data")
        val issues = JSONArray()
        val balances = JSONObject()
        val openRows = JSONObject()
        for (section in listOf("sales", "purchases")) {
            val sums = linkedMapOf<String, BigDecimal>()
            val open = JSONArray()
            val rows = data.getJSONArray(section)
            val seen = HashSet<String>()
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val id = row.optString("id").trim()
                val currency = row.optString("currency")
                val total = row.optString("total").toBigDecimalOrNull()
                val paid = row.optString("paid").toBigDecimalOrNull()
                if (id.isEmpty() || !seen.add(id) || currency !in currencies ||
                    total == null || paid == null || total.signum() < 0 ||
                    paid.signum() < 0 || paid > total) {
                    issues.put(JSONObject().put("section", section)
                        .put("row", index).put("reason", "invalid_opening_invoice"))
                    continue
                }
                val due = total.subtract(paid)
                if (due.signum() == 0) continue
                sums[currency] = (sums[currency] ?: BigDecimal.ZERO).add(due)
                open.put(JSONObject().put("sourceId", id)
                    .put("invoiceNo", row.optString("invoiceNo"))
                    .put("currency", currency).put("total", total.toPlainString())
                    .put("previouslyPaid", paid.toPlainString())
                    .put("remaining", due.toPlainString()))
            }
            val byCurrency = JSONObject()
            for ((currency, amount) in sums) byCurrency.put(currency, amount.toPlainString())
            balances.put(section, byCurrency)
            openRows.put(section, open)
        }
        return JSONObject().put("balancesByCurrency", balances)
            .put("openInvoices", openRows).put("issues", issues)
            .put("issueCount", issues.length())
            .put("requiresLiveBalanceReconciliation", true)
            .put("postsHistoricalTransactions", false)
    }
}
