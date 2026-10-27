package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject

/**
 * Whole-backup integrity preflight. Reads the legacy v2 snapshot without posting
 * historical movements or mutating its contents.
 */
object LegacyWholeBackupAudit {
    private val sections = listOf(
        "products", "maintenanceParts", "sales", "repairs", "expenses",
        "payments", "partnerWithdrawals", "cashTransactions", "dailyClosings",
        "purchases", "suppliers", "returns", "supplierLedger",
        "inventoryMovements", "productCostHistory", "loyaltyLedger",
        "usedWatchTransactions", "customerOrders", "auditLog", "discountRules",
        "smartReceipts", "productDetailOptions", "inventorySessions",
        "customerSessions", "shiftHandovers"
    )
    private val required = setOf(
        "products", "maintenanceParts", "sales", "repairs", "expenses",
        "payments", "partnerWithdrawals", "cashTransactions", "dailyClosings",
        "purchases", "suppliers", "returns", "supplierLedger"
    )

    fun inspect(root: JSONObject): JSONObject {
        require(root.optString("format") == "alsafa-accounting-backup" &&
            root.optInt("formatVersion") == 2) { "صيغة نسخة الصفا غير مدعومة" }
        val data = root.getJSONObject("data")
        val counts = JSONObject()
        val problems = JSONArray()
        val idsBySection = HashMap<String, Set<String>>()
        for (section in sections) {
            val rows = data.optJSONArray(section)
            if (rows == null) {
                if (section in required) problems.put(JSONObject()
                    .put("section", section).put("reason", "missing_section"))
                continue
            }
            counts.put(section, rows.length())
            val ids = HashSet<String>()
            for (i in 0 until rows.length()) {
                val item = rows.optJSONObject(i)
                if (item == null) {
                    problems.put(JSONObject().put("section", section).put("row", i)
                        .put("reason", "invalid_record"))
                    continue
                }
                val id = item.optString("id").trim()
                if (id.isEmpty() || !ids.add(id)) problems.put(JSONObject()
                    .put("section", section).put("row", i)
                    .put("reason", "missing_or_duplicate_id"))
            }
            idsBySection[section] = ids
        }
        val suppliers = idsBySection["suppliers"].orEmpty()
        val products = idsBySection["products"].orEmpty() +
            idsBySection["maintenanceParts"].orEmpty()
        val invoices = idsBySection["sales"].orEmpty() +
            idsBySection["repairs"].orEmpty() +
            idsBySection["purchases"].orEmpty()
        fun check(section: String, field: String, accepted: Set<String>) {
            val rows = data.optJSONArray(section) ?: return
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val ref = row.optString(field).trim()
                if (ref.isNotEmpty() && ref !in accepted) problems.put(JSONObject()
                    .put("section", section).put("row", i).put("field", field)
                    .put("reason", "unresolved_reference"))
            }
        }
        check("purchases", "supplierId", suppliers)
        check("supplierLedger", "supplierId", suppliers)
        check("productCostHistory", "productId", products)
        // Old payments and returns may legitimately reference deleted historical
        // invoices. Report them, but never silently rewrite the source snapshot.
        check("payments", "referenceId", invoices)
        check("returns", "referenceId", invoices)
        // Verify financial snapshot invariants without replaying historical
        // receipts, withdrawals or stock movements into the native ledger.
        val financial = JSONObject()
        var outstandingSales = 0
        var outstandingPurchases = 0
        for (section in listOf("sales", "purchases")) {
            val rows = data.optJSONArray(section) ?: continue
            var totalMinor = 0L
            var paidMinor = 0L
            var validRows = 0
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val total = row.optString("total").toBigDecimalOrNull()
                val paid = row.optString("paid").toBigDecimalOrNull()
                val currency = row.optString("currency")
                if (total == null || paid == null ||
                    total.signum() < 0 || paid.signum() < 0 ||
                    paid > total || currency !in setOf("USD", "TRY", "SYP")) {
                    problems.put(JSONObject().put("section", section).put("row", i)
                        .put("reason", "invalid_financial_balance"))
                    continue
                }
                // Do not add mixed currencies together. Each invoice is
                // reconciled independently and kept in its original currency.
                validRows++
                if (paid < total) {
                    if (section == "sales") outstandingSales++ else outstandingPurchases++
                }
            }
            financial.put(section + "Validated", validRows)
        }
        financial.put("outstandingSalesCount", outstandingSales)
            .put("outstandingPurchasesCount", outstandingPurchases)
        val historicalSections = listOf("sales", "purchases", "payments",
            "cashTransactions", "partnerWithdrawals", "dailyClosings",
            "expenses", "supplierLedger", "returns")
        val preserved = JSONObject()
        for (section in historicalSections) {
            preserved.put(section, data.optJSONArray(section)?.length() ?: 0)
        }
        return JSONObject().put("financial", financial).put("preservedHistoricalCounts", preserved).put("counts", counts).put("issues", problems)
            .put("issueCount", problems.length())
            .put("readyForUnreviewedPosting", false)
            .put("financialPosting", "NONE")
    }
}
