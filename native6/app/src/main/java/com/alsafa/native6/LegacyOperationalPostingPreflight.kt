package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Strict preflight for converting the restored financial archive into live
 * accounting. Never posts historical inventory or cash as new movements.
 * Reports blockers instead of silently guessing missing accounting details.
 */
object LegacyOperationalPostingPreflight {
    private val moneyFields = listOf("total", "paid")
    private val currencies = setOf("USD", "TRY", "SYP")
    private fun minor(raw: String): Long =
        BigDecimal(raw).movePointRight(2).longValueExact()

    fun inspect(store: Store): JSONObject {
        val metadata = LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val issues = JSONArray()
        val sections = JSONObject()
        val sourceCounts = metadata.getJSONObject("sourceCollections")
        val knownInvoices = mutableMapOf<String, String>()
        val numbers = mutableSetOf<String>()
        for (section in listOf("sales", "purchases")) {
            var valid = 0
            val source = store.legacyRows(section)
            require(source.size == sourceCounts.optInt(section, -1)) {
                "عدد فواتير $section غير مطابق للأرشيف"
            }
            for (row in source) {
                val key = row.getValue("legacy_id")
                val invoice = JSONObject(row.getValue("payload"))
                val id = invoice.optString("id").trim()
                val number = invoice.optString("invoiceNo").ifBlank {
                    invoice.optString("number")
                }.trim()
                val currency = invoice.optString("currency")
                val date = invoice.optString("date").take(10)
                val amounts = moneyFields.map { field ->
                    runCatching { minor(invoice.getString(field)) }.getOrNull()
                }
                val total = amounts[0]
                val paid = amounts[1]
                val reasons = mutableListOf<String>()
                if (id.isBlank() || knownInvoices.putIfAbsent("$section:$id", key) != null)
                    reasons.add("missing_or_duplicate_invoice_id")
                if (number.isBlank()) reasons.add("missing_invoice_number")
                else if (!numbers.add("$section:$number")) reasons.add("duplicate_invoice_number")
                if (currency !in currencies) reasons.add("invalid_currency")
                if (runCatching { LocalDate.parse(date) }.isFailure)
                    reasons.add("invalid_invoice_date")
                if (total == null || paid == null || total <= 0L || paid < 0L || paid > total)
                    reasons.add("invalid_invoice_amounts")
                val items = invoice.optJSONArray("items")
                if (items == null || items.length() == 0)
                    reasons.add("missing_invoice_items")
                else {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i)
                        if (item == null) {
                            reasons.add("invalid_item_$i")
                            continue
                        }
                        val productId = item.optString("productId").trim()
                        if (productId.isBlank()) reasons.add("item_without_product_id_$i")
                        else if (store.scalar("SELECT count(*) FROM products WHERE id=?", productId) == 0L)
                            reasons.add("unresolved_product_$i")
                    }
                }
                if (reasons.isEmpty()) valid++
                else issues.put(JSONObject().put("section", section)
                    .put("archiveKey", key).put("invoiceId", id)
                    .put("reasons", JSONArray(reasons)))
            }
            sections.put(section, JSONObject()
                .put("archived", source.size).put("validated", valid)
                .put("blocked", source.size - valid))
        }
        val linkage = runCatching { LegacyFinancialLinkage.inspect(store) }
        if (linkage.isFailure) {
            issues.put(JSONObject().put("section", "payments_and_returns")
                .put("reason", "linkage_inspection_failed")
                .put("detail", linkage.exceptionOrNull()?.message ?: "unknown"))
        } else if (!linkage.getOrThrow().optBoolean("complete")) {
            issues.put(JSONObject().put("section", "payments_and_returns")
                .put("reason", "unresolved_or_duplicate_references")
                .put("linkage", linkage.getOrThrow()))
        }
        // Invoice cumulative paid already includes old payments. Historical
        // payment rows must never be replayed as extra receipts or cash.
        return JSONObject()
            .put("sourceSha256", metadata.optString("sourceSha256"))
            .put("sections", sections)
            .put("blockers", issues)
            .put("canPost", issues.length() == 0)
            .put("postingPerformed", false)
            .put("historicalPaymentsAlreadyInInvoicePaid", true)
            .put("repostStock", false)
            .put("repostHistoricalCash", false)
    }
}
