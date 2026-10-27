package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LegacyWholeBackupAuditTest {
    private fun root(): JSONObject {
        val data = JSONObject()
        for (section in listOf("products", "maintenanceParts", "sales", "repairs",
            "expenses", "payments", "partnerWithdrawals", "cashTransactions",
            "dailyClosings", "purchases", "suppliers", "returns", "supplierLedger")) {
            data.put(section, JSONArray())
        }
        return JSONObject().put("format", "alsafa-accounting-backup")
            .put("formatVersion", 2).put("data", data)
    }

    @Test fun completeEmptySectionsAreAcceptedWithoutPosting() {
        val result = LegacyWholeBackupAudit.inspect(root())
        assertEquals(0, result.getInt("issueCount"))
        assertEquals("NONE", result.getString("financialPosting"))
        assertFalse(result.getBoolean("readyForUnreviewedPosting"))
    }

    @Test fun supplierLinkIsCheckedAgainstImportedSuppliers() {
        val root = root()
        root.getJSONObject("data").getJSONArray("purchases").put(
            JSONObject().put("id", "buy-1").put("supplierId", "missing"))
        val result = LegacyWholeBackupAudit.inspect(root)
        assertEquals(1, result.getInt("issueCount"))
        assertEquals("unresolved_reference",
            result.getJSONArray("issues").getJSONObject(0).getString("reason"))
    }

    @Test fun historicalPaymentWithoutInvoiceIsReportedNotDeleted() {
        val root = root()
        val payment = JSONObject().put("id", "pay-1").put("referenceId", "old-invoice")
        root.getJSONObject("data").getJSONArray("payments").put(payment)
        val result = LegacyWholeBackupAudit.inspect(root)
        assertEquals(1, result.getInt("issueCount"))
        assertEquals("old-invoice", payment.getString("referenceId"))
    }
}
