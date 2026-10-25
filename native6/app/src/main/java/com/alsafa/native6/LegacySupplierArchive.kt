package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject

/**
 * Supplier records preserved from Backup v2. Never treats a legacy supplier
 * field as a verified payable balance or posts historical supplier payments.
 */
object LegacySupplierArchive {
    fun page(store: Store, search: String = "", offset: Int = 0, limit: Int = 30): JSONObject {
        require(offset >= 0 && limit in 1..100)
        require(search.length <= 200)
        val metadata = LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val expected = metadata.getInt("suppliers")
        val rows = store.rows(
            "SELECT legacy_id,payload FROM legacy_records WHERE category=? ORDER BY legacy_id",
            "source_suppliers"
        )
        require(rows.size == expected) { "سجلات الموردين القديمة غير مكتملة" }
        val query = search.trim()
        val records = JSONArray()
        val ids = HashSet<String>()
        var matched = 0
        for (row in rows) {
            val original = JSONObject(row.getValue("payload"))
            val id = original.optString("id").trim()
            require(id.isNotEmpty() && ids.add(id) && id == row.getValue("legacy_id")) {
                "معرف مورد قديم مفقود أو غير مطابق"
            }
            require(store.scalar("SELECT count(*) FROM suppliers WHERE id=?", id) == 1L) {
                "سجل المورد الأصلي غير موجود في قاعدة البيانات"
            }
            val name = original.optString("name")
            val phone = original.optString("phone")
            if (query.isNotEmpty() && !listOf(id, name, phone).any {
                    it.contains(query, ignoreCase = true)
                }) continue
            if (matched >= offset && matched.toLong() < offset.toLong() + limit) {
                val native = store.rows(
                    "SELECT id,name,phone FROM suppliers WHERE id=?", id
                ).singleOrNull() ?: throw IllegalStateException("المورد $id غير موجود")
                records.put(JSONObject()
                    .put("id", id)
                    .put("name", name)
                    .put("phone", phone)
                    .put("nativeName", native.getValue("name"))
                    .put("nativePhone", native.getValue("phone"))
                    .put("original", original)
                    .put("balanceVerified", false))
            }
            matched++
        }
        return JSONObject()
            .put("records", records)
            .put("totalMatching", matched)
            .put("hasMore", offset.toLong() + records.length() < matched)
            .put("readOnly", true)
            .put("financialPosting", "NONE")
            .put("warning", "بيانات المورد الأصلية محفوظة؛ دين المورد يحتاج مطابقة مستقلة")
    }
}
