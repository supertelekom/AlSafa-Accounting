package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject

/** Verified historical movements, never re-posted as new transactions. */
object LegacyFinancialActivity {
    private val categories = setOf("returns", "cashTransactions", "payments", "dailyClosings",
        "sales", "purchases", "supplierLedger", "partnerWithdrawals")

    fun page(store: Store, category: String, from: String = "", to: String = "",
             offset: Int = 0, limit: Int = 50): JSONObject {
        require(category in categories)
        require(offset >= 0 && limit in 1..100)
        if (from.isNotBlank()) java.time.LocalDate.parse(from)
        if (to.isNotBlank()) java.time.LocalDate.parse(to)
        require(from.isBlank() || to.isBlank() || from <= to)
        require(offset <= 1000000)
        val metadata = LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val expected = metadata.getJSONObject("sourceCollections").optInt(category, 0)
        val rows = store.legacyRows(category)
        require(rows.size == expected) { "أرشيف الحركات غير مكتمل" }
        var missingDateCount = 0
        val matching = rows.asSequence().map { row ->
            val record = JSONObject(row.getValue("payload"))
            val date = listOf("actualDate", "date", "paidAt", "createdAt", "deliveredAt")
                .map { record.optString(it).take(10) }
                .firstOrNull { value -> runCatching { java.time.LocalDate.parse(value) }.isSuccess } ?: ""
            if (date.isBlank()) missingDateCount++
            JSONObject().put("archiveKey", row.getValue("legacy_id"))
                .put("date", date).put("record", record)
        }.filter { row ->
            val date = row.optString("date")
            (from.isBlank() || (date.isNotBlank() && date >= from)) &&
                (to.isBlank() || (date.isNotBlank() && date <= to))
        }.sortedWith(compareByDescending<JSONObject> { it.optString("date") }
            .thenByDescending { it.optString("archiveKey") })
        val page = JSONArray()
        matching.drop(offset).take(limit).forEach { page.put(it) }
        return JSONObject().put("category", category).put("total", matching.size)
            .put("records", page).put("offset", offset).put("limit", limit)
            .put("hasMore", offset + page.length() < matching.size)
            .put("missingDateCount", missingDateCount)
            .put("dateFilterMayExcludeRecords", missingDateCount > 0 && (from.isNotBlank() || to.isNotBlank()))
            .put("readOnly", true).put("posting", "NONE")
    }
}
