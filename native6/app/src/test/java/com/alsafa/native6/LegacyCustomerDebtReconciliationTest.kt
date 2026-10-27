package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LegacyCustomerDebtReconciliationTest {
    private fun invoice(id: String, status: String, customer: String, paid: String) =
        JSONObject().put("id", id).put("invoiceNo", "12")
            .put("total", "100").put("paid", paid)
            .put("currency", "USD").put("paymentStatus", status)
            .put("customer", customer)

    private fun root(vararg invoices: JSONObject) =
        JSONObject().put("format", "alsafa-accounting-backup")
            .put("formatVersion", 2)
            .put("data", JSONObject().put("sales", JSONArray().apply {
                invoices.forEach { put(it) }
            }))

    @Test fun paidInFullStatusWithStalePaidFieldMustNotBecomeDebt() {
        val result = LegacyCustomerDebtReconciliation.inspect(
            root(invoice("a", "مدفوع بالكامل", "Ali", "0")))
        assertEquals(1, result.getInt("settledStatusConflicts"))
        assertEquals(0, result.getInt("candidateCount"))
        assertFalse(result.getBoolean("safeForAutomaticActivation"))
    }

    @Test fun unknownCustomerBlocksActivation() {
        val result = LegacyCustomerDebtReconciliation.inspect(
            root(invoice("a", "دفعة جزئية", "", "25")))
        assertEquals(1, result.getInt("missingCustomerConflicts"))
    }

    @Test fun validUnpaidCustomerIsCandidateButNotPosted() {
        val result = LegacyCustomerDebtReconciliation.inspect(
            root(invoice("a", "دفعة جزئية", "Ali", "25")))
        assertEquals(1, result.getInt("candidateCount"))
        assertEquals("75", result.getJSONArray("candidates")
            .getJSONObject(0).getString("remaining"))
    }

    @Test fun closedInvoicesAreNotCandidates() {
        val result = LegacyCustomerDebtReconciliation.inspect(
            root(invoice("a", "مدفوع بالكامل", "", "100")))
        assertEquals(0, result.getInt("candidateCount"))
        assertEquals(0, result.getInt("conflictCount"))
    }
}
