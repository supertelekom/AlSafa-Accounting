package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Read-only view of historical supplier ledger entries. Entries are evidence,
 * not an additional payable or cash posting.
 */
object LegacySupplierLedgerView {
    fun page(
        store: Store,
        supplierId: String = "",
        from: String = "",
        to: String = "",
        offset: Int = 0,
        limit: Int = 30
    ): JSONObject {
        require(offset >= 0 && limit in 1..100)
        if (from.isNotEmpty()) java.time.LocalDate.parse(from)
        if (to.isNotEmpty()) java.time.LocalDate.parse(to)
        require(from.isEmpty() || to.isEmpty() || from <= to)
        val metadata = LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val expected = metadata.optJSONObject("sourceCollections")
            ?.optInt("supplierLedger", 0) ?: 0
        val rows = store.rows(
            "SELECT legacy_id,payload FROM legacy_records WHERE category=? ORDER BY legacy_id",
            "supplierLedger"
        )
        require(rows.size == expected) { "دفتر الموردين القديم غير مكتمل" }
        val records = JSONArray()
        val totals = linkedMapOf<String, BigDecimal>()
        var matched = 0
        for (row in rows) {
            val item = JSONObject(row.getValue("payload"))
            val id = item.optString("supplierId").trim()
            val date = item.optString("date").take(10)
            if (supplierId.isNotEmpty() && supplierId != id) continue
            if (from.isNotEmpty() || to.isNotEmpty()) {
                java.time.LocalDate.parse(date)
                if (from.isNotEmpty() && date < from) continue
                if (to.isNotEmpty() && date > to) continue
            }
            val currency = item.optString("currency")
            require(currency in setOf("USD", "TRY", "SYP")) {
                "عملة غير مدعومة في دفتر الموردين"
            }
            val amount = item.optString("amount").toBigDecimalOrNull()
                ?: throw IllegalArgumentException("مبلغ غير صالح في دفتر الموردين")
            require(amount.stripTrailingZeros().scale() <= 2 && amount.signum() >= 0) {
                "مبلغ دفتر الموردين غير صالح"
            }
            val kind = item.optString("kind")
            val key = "$kind|$currency"
            totals[key] = (totals[key] ?: BigDecimal.ZERO).add(amount)
            if (matched >= offset && matched.toLong() < offset.toLong() + limit) {
                records.put(JSONObject()
                    .put("supplierId", id)
                    .put("date", date)
                    .put("kind", kind)
                    .put("currency", currency)
                    .put("amount", amount.toPlainString())
                    .put("original", item))
            }
            matched++
        }
        val byKind = JSONObject()
        for ((key, amount) in totals) byKind.put(key, amount.toPlainString())
        return JSONObject()
            .put("records", records)
            .put("totalMatching", matched)
            .put("hasMore", offset.toLong() + records.length() < matched)
            .put("totalsByKindAndCurrency", byKind)
            .put("readOnly", true)
            .put("financialPosting", "NONE")
            .put("warning", "المبالغ مصنفة بحسب نوع القيد، وليست رصيد دين معتمداً؛ لا تكرر حسم المدفوعات")
    }
}
