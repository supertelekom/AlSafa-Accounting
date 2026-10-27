package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject

/**
 * Deterministic invoice identity preflight for an eventual atomic migration.
 * Does not insert invoices, stock movements, receipts or journal entries.
 */
object LegacyInvoiceMigrationPlan {
    fun inspect(store: Store): JSONObject {
        val meta = LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val counts = meta.getJSONObject("sourceCollections")
        val collisions = JSONArray()
        val invalid = JSONArray()
        val proposed = JSONArray()
        val existing = store.rows("SELECT id,number FROM invoices")
        val existingIds = existing.map { it.getValue("id") }.toSet()
        val existingNumbers = existing.map { it.getValue("number") }.toSet()
        val seenIds = mutableSetOf<String>()
        val seenNumbers = mutableSetOf<String>()
        for (section in listOf("sales", "purchases")) {
            val source = store.legacyRows(section)
            require(source.size == counts.optInt(section, -1)) {
                "عدد الفواتير القديمة غير مطابق: $section"
            }
            for (row in source) {
                val original = JSONObject(row.getValue("payload"))
                val sourceId = original.optString("id").trim()
                val number = original.optString("number").trim()
                val targetId = "legacy:$section:$sourceId"
                val currency = original.optString("currency").trim()
                val date = original.optString("date").take(10)
                val total = original.optString("total").toBigDecimalOrNull()
                val detail = JSONObject().put("section", section)
                    .put("sourceId", sourceId).put("sourceNumber", number)
                    .put("targetId", targetId)
                if (sourceId.isBlank() || !seenIds.add(targetId)) {
                    invalid.put(detail.put("reason", "missing_or_duplicate_id"))
                    continue
                }
                if (currency !in setOf("USD", "TRY", "SYP") ||
                    runCatching { java.time.LocalDate.parse(date) }.isFailure ||
                    total == null || total.signum() < 0 || total.stripTrailingZeros().scale() > 2) {
                    invalid.put(JSONObject(detail.toString()).put("reason", "invalid_financial_fields"))
                }
                if (number.isNotBlank() && !seenNumbers.add(number)) {
                    collisions.put(JSONObject(detail.toString()).put("reason", "shared_legacy_number"))
                }
                if (targetId in existingIds || (number.isNotBlank() && number in existingNumbers)) {
                    collisions.put(detail)
                }
                proposed.put(detail)
            }
        }
        return JSONObject().put("sourceSha256", meta.optString("sourceSha256"))
            .put("invoiceCount", proposed.length())
            .put("invalid", invalid).put("collisions", collisions)
            .put("identitySafe", invalid.length() == 0 && collisions.length() == 0)
            .put("stockRepostingAllowed", false)
            .put("cashRepostingAllowed", false)
            .put("posting", "NONE")
    }
}
