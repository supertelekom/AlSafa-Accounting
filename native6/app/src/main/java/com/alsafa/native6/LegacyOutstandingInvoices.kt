package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Paginated read-only historical receivables/payables by invoice.
 * Snapshot paid amounts are cumulative; never add independent payment records.
 */
object LegacyOutstandingInvoices {
    fun page(store: Store, section: String, currency: String = "",
             offset: Int = 0, limit: Int = 50): JSONObject {
        require(section == "sales" || section == "purchases") { "نوع فاتورة غير مدعوم" }
        require(currency.isEmpty() || currency in setOf("USD", "TRY", "SYP")) { "عملة غير مدعومة" }
        require(offset >= 0 && limit in 1..200) { "حدود الصفحة غير صالحة" }
        // Verify the complete archive and import metadata before displaying any balances.
        LegacyArchivedDebtReport.inspect(store)
        val rows = store.rows(
            "SELECT legacy_id,payload FROM legacy_records WHERE category=? ORDER BY legacy_id", section
        )
        val matches = ArrayList<JSONObject>()
        val seen = HashSet<String>()
        for (row in rows) {
            val invoice = JSONObject(row.getValue("payload"))
            val id = invoice.optString("id").trim()
            require(id.isNotEmpty() && seen.add(id)) { "معرف فاتورة مفقود أو مكرر" }
            val unit = invoice.optString("currency")
            require(unit in setOf("USD", "TRY", "SYP")) { "عملة فاتورة غير مدعومة" }
            fun money(key: String): BigDecimal {
                require(invoice.has(key) && !invoice.isNull(key)) { "حقل $key مفقود في $id" }
                val amount = invoice.optString(key).toBigDecimalOrNull()
                    ?: throw IllegalArgumentException("مبلغ $key غير صالح في $id")
                require(amount.stripTrailingZeros().scale() <= 2) { "كسور غير مدعومة في $id" }
                return amount
            }
            val total = money("total")
            val paid = money("paid")
            require(total.signum() >= 0 && paid.signum() >= 0 && paid <= total) {
                "مبالغ غير متطابقة في $id"
            }
            val due = total.subtract(paid)
            if (due.signum() == 0 || (currency.isNotEmpty() && currency != unit)) continue
            matches.add(JSONObject()
                .put("archiveKey", row.getValue("legacy_id"))
                .put("invoiceId", id)
                .put("date", invoice.optString("date"))
                .put("customer", invoice.optString("customer"))
                .put("supplierName", invoice.optString("supplierName"))
                .put("phone", invoice.optString("phone"))
                .put("currency", unit)
                .put("total", total.toPlainString())
                .put("paidSnapshot", paid.toPlainString())
                .put("outstandingSnapshot", due.toPlainString()))
        }
        val result = JSONArray()
        matches.drop(offset).take(limit).forEach { result.put(it) }
        return JSONObject()
            .put("section", section)
            .put("currency", currency)
            .put("totalOutstandingInvoices", matches.size)
            .put("offset", offset)
            .put("limit", limit)
            .put("records", result)
            .put("readOnly", true)
            .put("financialPosting", "NONE")
            .put("warning", "الأرصدة أولية؛ المرتجعات ودفتر الموردين لم تتم مطابقتهما")
    }
}
