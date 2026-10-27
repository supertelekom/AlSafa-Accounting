package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LegacySalesActivationAuditTest {
    private fun backup(products: JSONArray, sales: JSONArray): JSONObject =
        JSONObject().put("format", "alsafa-accounting-backup").put("formatVersion", 2)
            .put("data", JSONObject().put("products", products).put("sales", sales))

    private fun sale(id: String, paid: String, productId: String): JSONObject =
        JSONObject().put("id", id).put("invoiceNo", if (paid == "30.00") 1 else if (paid == "5") 2 else 3).put("currency", "USD").put("total", "25.00").put("paid", paid)
            .put("items", JSONArray().put(JSONObject().put("productId", productId).put("qty", 1)))

    @Test fun validUnpaidSaleCanBeReconciledWithoutPosting() {
        val result = LegacySalesActivationAudit.inspect(backup(
            JSONArray().put(JSONObject().put("id", "p1")),
            JSONArray().put(sale("s1", "10.00", "p1"))))
        assertTrue(result.getBoolean("activationPermitted"))
        assertEquals(1, result.getInt("unpaidInvoices"))
        assertEquals(1, result.getInt("linkedProductLines"))
        assertEquals("NONE", result.getString("financialPosting"))
    }

    @Test fun missingProductReferenceBlocksActivation() {
        val result = LegacySalesActivationAudit.inspect(backup(
            JSONArray().put(JSONObject().put("id", "p1")),
            JSONArray().put(sale("s1", "10.00", "missing"))))
        assertFalse(result.getBoolean("activationPermitted"))
        assertEquals("missing_product_reference",
            result.getJSONArray("issues").getJSONObject(0).getString("reason"))
    }

    @Test fun duplicateInvoiceNumberIsRejectedEvenWithDifferentIds() {
        val first = sale("s1", "10.00", "p1").put("invoiceNo", 500)
        val second = sale("s2", "10.00", "p1").put("invoiceNo", 500)
        val result = LegacySalesActivationAudit.inspect(backup(
            JSONArray().put(JSONObject().put("id", "p1")),
            JSONArray().put(first).put(second)))
        assertTrue(result.getBoolean("activationPermitted"))
        assertEquals("duplicate_display_invoice_number",
            result.getJSONArray("warnings").getJSONObject(0).getString("reason"))
    }

    @Test fun overpaidInvoiceAndDuplicateIdAreRejected() {
        val result = LegacySalesActivationAudit.inspect(backup(
            JSONArray().put(JSONObject().put("id", "p1")),
            JSONArray().put(sale("s1", "30.00", "p1")).put(sale("s1", "5", "p1"))))
        assertFalse(result.getBoolean("activationPermitted"))
        assertEquals(2, result.getJSONArray("issues").length())
    }
}
