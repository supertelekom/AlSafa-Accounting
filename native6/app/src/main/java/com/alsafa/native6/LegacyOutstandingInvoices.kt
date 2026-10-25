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
             offset: Int = 0, limit: Int = 50, from: String = "", to: String = "",
             query: String = ""): JSONObject {
        require(section == "sales" || section == "purchases") { "نوع فاتورة غير مدعوم" }
        require(currency.isEmpty() || currency in setOf("USD", "TRY", "SYP")) { "عملة غير مدعومة" }
        require(offset >= 0 && limit in 1..200) { "حدود الصفحة غير صالحة" }
        if (from.isNotEmpty()) java.time.LocalDate.parse(from)
        if (to.isNotEmpty()) java.time.LocalDate.parse(to)
        require(from.isEmpty() || to.isEmpty() || from <= to) { "نطاق التاريخ غير صالح" }
        // Verify the complete archive and import metadata before displaying any balances.
        LegacyArchivedDebtReport.inspect(store)
        val rows = store.rows(
            "SELECT legacy_id,payload FROM legacy_records WHERE category=? ORDER BY legacy_id", section
        )
        val matches = ArrayList<JSONObject>()
        var scanned = 0
        val pageRows = JSONArray()
        val outstandingTotals = linkedMapOf<String, BigDecimal>()
        val search = query.trim()
        require(search.length <= 200) { "عبارة البحث طويلة جداً" }
        val seen = HashSet<String>()
        for (row in rows) {
            scanned++
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
            val date = invoice.optString("date").take(10)
            if (from.isNotEmpty() || to.isNotEmpty()) {
                require(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "تاريخ الفاتورة $id غير صالح" }
                java.time.LocalDate.parse(date)
            }
            if (from.isNotEmpty() && (date.isEmpty() || date < from)) continue
            if (to.isNotEmpty() && (date.isEmpty() || date > to)) continue
            if (search.isNotEmpty()) {
                val fields = listOf("id", "invoiceNo", "customer", "customerName", "phone",
                    "supplierName", "supplierId", "notes")
                val direct = fields.any { invoice.optString(it).contains(search, ignoreCase = true) }
                val items = invoice.optJSONArray("items")
                val nested = items != null && (0 until items.length()).any { index ->
                    val item = items.optJSONObject(index)
                    item != null && listOf("name", "productName", "partName", "barcode", "sku", "model")
                        .any { item.optString(it).contains(search, ignoreCase = true) }
                }
                if (!direct && !nested) continue
            }
            outstandingTotals[unit] = (outstandingTotals[unit] ?: BigDecimal.ZERO).add(due)
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
        val totalsJson = JSONObject()
        for ((unit, amount) in outstandingTotals) totalsJson.put(unit, amount.toPlainString())
        matches.drop(offset).take(limit).forEach { pageRows.put(it) }
        return JSONObject()
            .put("section", section)
            .put("currency", currency)
            .put("from", from)
            .put("to", to)
            .put("query", query)
            .put("outstandingTotalsByCurrency", totalsJson)
            .put("totalOutstandingInvoices", matches.size)
            .put("scannedInvoices", scanned)
            .put("offset", offset)
            .put("limit", limit)
            .put("records", pageRows)
            .put("hasMore", offset.toLong() + pageRows.length() < matches.size)
            .put("nextOffset", if (offset.toLong() + pageRows.length() < matches.size) offset + pageRows.length() else -1)
            .put("readOnly", true)
            .put("financialPosting", "NONE")
            .put("warning", "الأرصدة أولية؛ المرتجعات ودفتر الموردين لم تتم مطابقتهما")
    }
}
