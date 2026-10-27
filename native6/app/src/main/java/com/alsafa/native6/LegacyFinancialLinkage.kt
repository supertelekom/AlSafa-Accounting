package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject

/** Check links in restored financial records without changing balances. */
object LegacyFinancialLinkage {
    fun inspect(store: Store): JSONObject {
        val meta = LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val counts = meta.getJSONObject("sourceCollections")
        val identifiers = linkedMapOf<String, Set<String>>()
        val duplicates = JSONArray()
        for (section in listOf("sales", "purchases", "repairs")) {
            if (section == "repairs") {
                val repairIds = store.rows("SELECT id,details FROM repairs")
                    .filter { row -> runCatching {
                        JSONObject(row["details"].orEmpty()).optBoolean("_legacyImported",false)
                    }.getOrDefault(false) }
                    .map { it.getValue("id") }
                require(repairIds.size == counts.optInt("repairs", -1)) { "عدد الصيانة المستعادة لا يطابق النسخة الأصلية" }
                require(repairIds.distinct().size == repairIds.size) { "معرفات الصيانة المستعادة مكررة" }
                identifiers[section] = repairIds.toSet()
                continue
            }
            val rows = store.legacyRows(section)
            val expected = counts.optInt(section, -1)
            require(expected >= 0 && rows.size == expected) { "قسم غير مكتمل: $section" }
            val ids = mutableSetOf<String>()
            for (row in rows) {
                val id = JSONObject(row.getValue("payload")).optString("id").trim()
                if (id.isBlank() || !ids.add(id)) {
                    duplicates.put(JSONObject().put("section", section)
                        .put("archiveKey", row.getValue("legacy_id")).put("id", id))
                }
            }
            identifiers[section] = ids
        }
        val missing = JSONArray()
        val unresolved = JSONArray()
        val duplicateMovements = JSONArray()
        val linked = JSONObject()
        for (section in listOf("payments", "returns")) {
            val rows = store.legacyRows(section)
            require(rows.size == counts.optInt(section, -1)) { "قسم غير مكتمل: $section" }
            val seen = mutableSetOf<String>()
            for (row in rows) {
                val movement = JSONObject(row.getValue("payload"))
                val id = movement.optString("id").trim()
                val key = row.getValue("legacy_id")
                if (id.isBlank() || !seen.add(id)) {
                    duplicateMovements.put(JSONObject().put("section", section)
                        .put("archiveKey", key).put("id", id))
                }
                val target = when (movement.optString("referenceType").trim().lowercase()) {
                    "sale", "sales" -> "sales"
                    "purchase", "purchases" -> "purchases"
                    "repair", "repairs" -> "repairs"
                    else -> ""
                }
                val referenceId = movement.optString("referenceId").trim()
                val detail = JSONObject().put("section", section)
                    .put("archiveKey", key).put("referenceType", target)
                    .put("referenceId", referenceId)
                if (referenceId.isBlank()) missing.put(detail)
                else if (target.isBlank() || referenceId !in (identifiers[target] ?: emptySet())) {
                    unresolved.put(detail)
                } else {
                    val targetKey = "$target:$referenceId"
                    val group = linked.optJSONArray(targetKey) ?: JSONArray()
                    group.put(JSONObject().put("section", section).put("archiveKey", key))
                    linked.put(targetKey, group)
                }
            }
        }
        val complete = missing.length() == 0 && unresolved.length() == 0 &&
            duplicates.length() == 0 && duplicateMovements.length() == 0
        return JSONObject().put("complete", complete)
            .put("missingReferences", missing)
            .put("unresolvedReferences", unresolved)
            .put("duplicateInvoiceIds", duplicates)
            .put("duplicateMovementIds", duplicateMovements)
            .put("linkedMovements", linked)
            .put("posting", "NONE")
    }
}
