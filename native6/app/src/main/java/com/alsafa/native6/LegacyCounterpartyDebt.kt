package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Read-only outstanding debt grouped by the actual historical counterparty.
 * Uses cumulative paid in each invoice snapshot, never replays payment events,
 * inventory movements or historical sales into the operational ledger.
 */
object LegacyCounterpartyDebt {
    private val currencies = setOf("USD", "TRY", "SYP")

    fun inspect(store: Store): JSONObject {
        val meta = LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val expected = meta.getJSONObject("sourceCollections")
        val result = JSONObject()
        for (section in listOf("sales", "purchases")) {
            val rows = store.rows(
                "SELECT legacy_id,payload FROM legacy_records WHERE category=? ORDER BY legacy_id",
                section
            )
            require(rows.size.toLong() == expected.optLong(section, 0)) {
                "أرشيف الفواتير غير مكتمل: $section"
            }
            val seen = hashSetOf<String>()
            val invoiceNumbers = hashSetOf<String>()
            val duplicateNumbers = JSONArray()
            val totals = sortedMapOf<String, Long>()
            val invoiceCounts = sortedMapOf<String, Int>()
            val missingNames = JSONArray()
            val openInvoices = JSONArray()
            val currencyTotals = sortedMapOf<String, Long>()
            var openCount = 0
            for (record in rows) {
                val invoice = JSONObject(record.getValue("payload"))
                val id = invoice.optString("id").trim()
                require(id.isNotEmpty() && seen.add(id)) {
                    "معرف فاتورة تاريخية مفقود أو مكرر: $section"
                }
                require(record.getValue("legacy_id").substringAfter(":") == id) {
                    "مفتاح الفاتورة التاريخية لا يطابق المصدر: $id"
                }
                val number = invoice.optString("invoiceNo").trim()
                if (number.isNotEmpty() && !invoiceNumbers.add(number)) {
                    duplicateNumbers.put(JSONObject().put("invoiceId", id).put("invoiceNo", number))
                }
                val date = invoice.optString("date").take(10)
                require(runCatching { java.time.LocalDate.parse(date) }.isSuccess) {
                    "تاريخ فاتورة تاريخية غير صالح: $id"
                }
                val currency = invoice.optString("currency")
                require(currency in currencies) { "عملة فاتورة غير مدعومة: $id" }
                val total = minor(invoice, "total")
                val paid = minor(invoice, "paid")
                require(paid <= total) { "المدفوع يتجاوز إجمالي الفاتورة: $id" }
                val due = Math.subtractExact(total, paid)
                currencyTotals[currency] = Math.addExact(currencyTotals[currency] ?: 0L, due)
                if (due == 0L) continue
                openCount++
                val name = if (section == "sales") {
                    invoice.optString("customer").trim()
                } else {
                    invoice.optString("supplierName").trim()
                }
                val party = name.ifEmpty {
                    missingNames.put(JSONObject().put("invoiceId", id)
                        .put("section", section).put("currency", currency))
                    "غير محدد"
                }
                openInvoices.put(JSONObject().put("invoiceId", id)
                    .put("invoiceNo", invoice.optString("invoiceNo"))
                    .put("date", invoice.optString("date").take(10))
                    .put("counterparty", party).put("currency", currency)
                    .put("outstanding", BigDecimal.valueOf(due, 2).toPlainString()))
                val key = "$party|$currency"
                totals[key] = Math.addExact(totals[key] ?: 0L, due)
                invoiceCounts[key] = Math.addExact(invoiceCounts[key] ?: 0, 1)
            }
            val groups = JSONArray()
            for ((key, value) in totals) {
                val cut = key.lastIndexOf('|')
                groups.put(JSONObject()
                    .put("counterparty", key.substring(0, cut))
                    .put("currency", key.substring(cut + 1))
                    .put("invoiceCount", invoiceCounts.getValue(key))
                    .put("outstanding", BigDecimal.valueOf(value, 2).toPlainString()))
            }
            val reconciled = sortedMapOf<String, Long>()
            for ((key, amount) in totals) {
                val currency = key.substringAfterLast("|")
                reconciled[currency] = Math.addExact(reconciled[currency] ?: 0L, amount)
            }
            require(reconciled == currencyTotals.filterValues { it != 0L }) {
                "إجمالي ديون الأطراف لا يطابق مجموع الفواتير: $section"
            }
            require(openInvoices.length() == openCount) {
                "عدد الفواتير المفتوحة لا يطابق التفاصيل: $section"
            }
            val perCurrency = JSONObject()
            for ((currency, amount) in currencyTotals) {
                perCurrency.put(currency, BigDecimal.valueOf(amount, 2).toPlainString())
            }
            result.put(section, JSONObject()
                .put("outstandingByCurrency", perCurrency)
                .put("groups", groups)
                .put("openInvoices", openInvoices)
                .put("openInvoiceCount", openCount)
                .put("missingCounterpartyNames", missingNames)
                .put("duplicateInvoiceNumbers", duplicateNumbers)
                .put("requiresManualReview", missingNames.length() > 0 || duplicateNumbers.length() > 0))
        }
        return JSONObject()
            .put("positions", result)
            .put("posting", "NONE")
            .put("readOnly", true)
            .put("basis", "INVOICE_TOTAL_MINUS_CUMULATIVE_PAID")
            .put("warning", "الأرصدة التاريخية للمطابقة فقط؛ لا تُضاف للذمم التشغيلية تلقائيًا")
    }

    private fun minor(row: JSONObject, key: String): Long {
        val number = row.optString(key, "0").toBigDecimalOrNull()
            ?: error("مبلغ غير صالح: $key")
        require(number.signum() >= 0) { "مبلغ سالب غير صالح: $key" }
        require(number.stripTrailingZeros().scale() <= 2) {
            "مبلغ بدقة تتجاوز أجزاء العملة: $key"
        }
        return number.movePointRight(2).longValueExact()
    }

    fun summary(store: Store): String {
        val report = inspect(store).getJSONObject("positions")
        val lines = mutableListOf("ديون الفواتير التاريخية حسب العميل أو المورد — قراءة فقط")
        for (section in listOf("sales", "purchases")) {
            val position = report.getJSONObject(section)
            lines.add(if (section == "sales") "العملاء:" else "الموردون (فواتير الشراء فقط):")
            val groups = position.getJSONArray("groups")
            for (i in 0 until groups.length()) {
                val row = groups.getJSONObject(i)
                lines.add("${row.getString("counterparty")} — ${row.getString("outstanding")} " +
                    "${row.getString("currency")} (${row.getInt("invoiceCount")} فواتير)")
            }
            if (groups.length() == 0) lines.add("لا توجد فواتير غير مسددة")
            val missing = position.getJSONArray("missingCounterpartyNames").length()
            if (missing > 0) lines.add("تنبيه: $missing فواتير دون اسم طرف، تحتاج مطابقة")
            val duplicates = position.getJSONArray("duplicateInvoiceNumbers").length()
            if (duplicates > 0) lines.add("تنبيه: $duplicates أرقام فواتير متكررة تحتاج مراجعة")
        }
        lines.add("لا يشمل دين الموردين الافتتاحي المنفصل؛ لا توجد قيود مالية جديدة.")
        return lines.joinToString("\n")
    }
}
