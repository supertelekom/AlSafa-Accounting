package com.alsafa.native6

import org.json.JSONObject
import org.json.JSONArray
import java.math.BigDecimal

/**
 * Operational restore gate: checks whether old sales can be activated against
 * imported product IDs without replaying historic stock or cash events.
 * This is a read-only audit. It does not mark invoices operational.
 */
object LegacySalesActivationAudit {
    fun inspect(root: JSONObject): JSONObject {
        require(root.optString("format") == "alsafa-accounting-backup" &&
            root.optInt("formatVersion") == 2) { "صيغة النسخة القديمة غير مدعومة" }
        val data = root.getJSONObject("data")
        val products = data.getJSONArray("products")
        val sales = data.getJSONArray("sales")
        val ids = HashSet<String>()
        for (i in 0 until products.length()) {
            val id = products.getJSONObject(i).optString("id").trim()
            require(id.isNotEmpty() && ids.add(id)) { "معرف منتج مفقود أو مكرر" }
        }
        val invoiceIds = HashSet<String>()
        val invoiceNumbers = HashSet<Long>()
        val warnings = JSONArray()
        val currencies = setOf("USD", "TRY", "SYP")
        val issues = JSONArray()
        var unpaid = 0
        var linkedLines = 0
        for (i in 0 until sales.length()) {
            val sale = sales.getJSONObject(i)
            val id = sale.optString("id").trim()
            if (id.isEmpty() || !invoiceIds.add(id)) {
                issues.put(JSONObject().put("row", i).put("reason", "duplicate_or_missing_invoice_id"))
            }
            val number = sale.optString("invoiceNo").toLongOrNull()
            if (number == null || number <= 0) {
                issues.put(JSONObject().put("row", i).put("reason", "missing_invoice_number"))
            } else if (!invoiceNumbers.add(number)) {
                // Real legacy backups can reuse printed invoice numbers. The
                // immutable source ID, not the display number, is the key.
                warnings.put(JSONObject().put("row", i).put("reason", "duplicate_display_invoice_number"))
            }
            if (sale.optString("currency") !in currencies) {
                issues.put(JSONObject().put("row", i).put("reason", "invalid_invoice_currency"))
            }
            val total = sale.optString("total").toBigDecimalOrNull()
            val paid = sale.optString("paid").toBigDecimalOrNull()
            if (total == null || paid == null || total < BigDecimal.ZERO ||
                paid < BigDecimal.ZERO || paid > total) {
                issues.put(JSONObject().put("row", i).put("reason", "invalid_payment_balance"))
            } else if (paid < total) unpaid++
            val lines = sale.optJSONArray("items")
            if (lines == null || lines.length() == 0) {
                issues.put(JSONObject().put("row", i).put("reason", "missing_invoice_lines"))
                continue
            }
            for (j in 0 until lines.length()) {
                val item = lines.optJSONObject(j)
                if (item == null) {
                    issues.put(JSONObject().put("row", i).put("line", j).put("reason", "invalid_line"))
                    continue
                }
                val productId = item.optString("productId").trim()
                if (productId.isNotEmpty()) {
                    if (productId !in ids) issues.put(JSONObject()
                        .put("row", i).put("line", j).put("reason", "missing_product_reference"))
                    else linkedLines++
                }
                val qty = item.optString("qty").toBigDecimalOrNull()
                if (qty == null || qty <= BigDecimal.ZERO) issues.put(JSONObject()
                    .put("row", i).put("line", j).put("reason", "invalid_quantity"))
            }
        }
        return JSONObject()
            .put("products", products.length())
            .put("sales", sales.length())
            .put("unpaidInvoices", unpaid)
            .put("linkedProductLines", linkedLines)
            .put("issues", issues)
            .put("warnings", warnings)
            .put("activationPermitted", issues.length() == 0)
            .put("financialPosting", "NONE")
    }
}
