package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only, whole-backup continuity audit. No historical cash, profit or stock
 * movements are posted by this check.
 */
object LegacyWholeBackupAudit {
    private val sections = listOf(
        "products", "maintenanceParts", "sales", "repairs", "expenses",
        "payments", "partnerWithdrawals", "cashTransactions", "dailyClosings",
        "purchases", "suppliers", "returns", "supplierLedger",
        "inventoryMovements", "productCostHistory", "loyaltyLedger"
    )

    fun inspect(root: JSONObject): JSONObject {
        require(root.optString("format") == "alsafa-accounting-backup" &&
            root.optInt("formatVersion") == 2) { "صيغة نسخة الصفا غير مدعومة" }
        val data = root.getJSONObject("data")
        val counts = JSONObject()
        val problems = JSONArray()
        val allIds = HashMap<String, Set<String>>()
        for (section in sections) {
            val rows = data.optJSONArray(section)
            if (rows == null) {
                problems.put(JSONObject().put("section", section).put("reason", "missing_section"))
                continue
            }
            counts.put(section, rows.length())
            val ids = HashSet<String>()
            for (i in 0 until rows.length()) {
                val item = rows.optJSONObject(i)
                val id = item?.optString("id")?.trim().orEmpty()
                if (id.isEmpty() || !ids.add(id)) problems.put(JSONObject()
                    .put("section", section).put("row", i).put("reason", "missing_or_duplicate_id"))
            }
            allIds[section] = ids
        }
        val suppliers = allIds["suppliers"].orEmpty()
        val products = allIds["products"].orEmpty() + allIds["maintenanceParts"].orEmpty()
        val sales = allIds["sales"].orEmpty()
        val repairs = allIds["repairs"].orEmpty()
        val purchases = allIds["purchases"].orEmpty()
        fun check(section: String, field: String, accepted: Set<String>) {
            val rows = data.optJSONArray(section) ?: return
            for (i in 0 until rows.length()) {
                val ref = rows.getJSONObject(i).optString(field).trim()
                if (ref.isNotEmpty() && ref !in accepted) problems.put(JSONObject()
                    .put("section", section).put("row", i).put("field", field)
                    .put("reason", "unresolved_reference"))
            }
        }
        check("purchases", "supplierId", suppliers)
        check("supplierLedger", "supplierId", suppliers)
        check("productCostHistory", "productId", products)
        val movements = data.optJSONArray("inventoryMovements")
        if (movements != null) for (i in 0 until movements.length()) {
            val row = movements.getJSONObject(i)
            val itemId = row.optString("itemId").trim()
            // Historical movement may refer to a legitimately deleted product:
            // report for review, never mutate or silently discard.
            if (itemId.isNotEmpty() && itemId !in products) problems.put(JSONObject()
                .put("section", "inventoryMovements").put("row", i)
                .put("reason", "historical_product_not_in_current_stock"))
        }
        val paymentRows = data.optJSONArray("payments")
        if (paymentRows != null) for (i in 0 until paymentRows.length()) {
            val row = paymentRows.getJSONObject(i)
            val ref = row.optString("referenceId").trim()
            if (ref.isNotEmpty() && ref !in sales && ref !in repairs && ref !in purchases)
                problems.put(JSONObject().put("section", "payments").put("row", i)
                    .put("reason", "historical_payment_reference_not_in_snapshot"))
        }
        return JSONObject().put("counts", counts).put("issues", problems)
            .put("issueCount", problems.length())
            .put("readyForUnreviewedPosting", false)
            .put("financialPosting", "NONE")
    }
}
