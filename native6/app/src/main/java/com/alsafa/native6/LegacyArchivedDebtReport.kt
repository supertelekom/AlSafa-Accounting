package com.alsafa.native6

import org.json.JSONObject
import java.math.BigDecimal

/**
 * Computes historical invoice balances from the imported, read-only archive.
 * Never posts a payment, changes a supplier balance, or adds archived cash to the ledger.
 */
object LegacyArchivedDebtReport {
    private val currencies = setOf("USD", "TRY", "SYP")

    fun inspect(store: Store): JSONObject {
        val imported = store.rows(
            "SELECT value FROM settings WHERE key=?", "legacy_snapshot_import"
        ).firstOrNull() ?: throw IllegalStateException("لم يتم استيراد أرشيف النسخة القديمة")
        val metadata = JSONObject(imported.getValue("value"))
        require(metadata.optString("financialPostingStatus") == "NOT_POSTED") {
            "يجب استخدام تقرير الأرشيف فقط قبل تفعيل الحسابات التاريخية"
        }
        val report = JSONObject()
        for (section in listOf("sales", "purchases")) {
            val rows = store.rows(
                "SELECT legacy_id,payload FROM legacy_records WHERE category=? ORDER BY legacy_id",
                section
            )
            val expected = metadata.optJSONObject("sourceCollections")?.optInt(section, -1) ?: -1
            require(expected >= 0 && rows.size == expected) { "$section: عدد الفواتير المؤرشفة لا يطابق النسخة الأصلية" }
            val totals = linkedMapOf<String, Array<BigDecimal>>()
            val seen = HashSet<String>()
            for (row in rows) {
                val invoice = JSONObject(row.getValue("payload"))
                val id = invoice.optString("id").trim()
                require(id.isNotEmpty() && seen.add(id)) { "$section: معرف فاتورة مفقود أو مكرر" }
                val currency = invoice.optString("currency")
                require(currency in currencies) { "$section/$id: عملة غير مدعومة" }
                fun money(field: String): BigDecimal {
                    val amount = invoice.optString(field).takeIf { invoice.has(field) && !invoice.isNull(field) }?.toBigDecimalOrNull()
                        ?: throw IllegalArgumentException("$section/$id: مبلغ $field غير صالح")
                    require(amount.stripTrailingZeros().scale() <= 2) {
                        "$section/$id: كسور غير مدعومة في $field"
                    }
                    return amount
                }
                val total = money("total")
                val paid = money("paid")
                require(total.signum() >= 0 && paid.signum() >= 0 && paid <= total) {
                    "$section/$id: المبلغ المدفوع لا يطابق إجمالي الفاتورة"
                }
                val values = totals.getOrPut(currency) {
                    arrayOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                }
                values[0] = values[0].add(total)
                values[1] = values[1].add(paid)
                values[2] = values[2].add(total.subtract(paid))
            }
            val sectionReport = JSONObject()
            for ((currency, values) in totals) {
                sectionReport.put(currency, JSONObject()
                    .put("invoiced", values[0].toPlainString())
                    .put("paidSnapshot", values[1].toPlainString())
                    .put("outstandingSnapshot", values[2].toPlainString()))
            }
            report.put(section, JSONObject()
                .put("invoiceCount", rows.size)
                .put("currencies", sectionReport))
        }
        val pending = listOf("payments", "supplierLedger", "returns", "cashTransactions", "partnerWithdrawals", "dailyClosings")
        val archiveCounts = JSONObject()
        for (section in pending) {
            val expected = metadata.optJSONObject("sourceCollections")?.optInt(section, -1) ?: -1
            val actual = store.scalar("SELECT count(*) FROM legacy_records WHERE category=?", section)
            require(expected >= 0 && actual == expected.toLong()) { "$section: أرشيف مالي غير مكتمل" }
            archiveCounts.put(section, actual)
        }
        report.put("relatedArchiveCounts", archiveCounts)
        return report.put("readOnly", true)
            .put("financialPosting", "NONE")
            .put("supplierLedgerReconciled", false)
            .put("returnsReconciled", false)
            .put("cashBalancesActivated", false)
            .put("warning", "المرتجعات ودفتر الموردين تحتاج مطابقة قبل اعتماد هذه الأرصدة")
    }
}
