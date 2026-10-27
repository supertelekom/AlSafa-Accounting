package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LegacyOnlineCollectionReconciliationTest {
    private fun root(sales: JSONArray, payments: JSONArray, returns: JSONArray = JSONArray()) =
        JSONObject().put("format", "alsafa-accounting-backup")
            .put("formatVersion", 2)
            .put("data", JSONObject().put("sales", sales)
                .put("payments", payments).put("returns", returns))
    private fun sale(status: String = "دفعة جزئية") =
        JSONObject().put("id", "sale1").put("invoiceNo", 231)
            .put("total", "162").put("paid", "0").put("currency", "USD")
            .put("saleType", "online").put("paymentStatus", status)
    private fun payment(amount: String) =
        JSONObject().put("referenceType", "sale").put("referenceId", "sale1")
            .put("currency", "USD").put("amount", amount)

    @Test fun partialOnlineCollectionLeavesOnlyEleven() {
        val result = LegacyCustomerDebtReconciliation.inspect(root(
            JSONArray().put(sale()), JSONArray().put(payment("151"))))
        assertEquals(1, result.getInt("candidateCount"))
        val candidate = result.getJSONArray("candidates").getJSONObject(0)
        assertEquals("11", candidate.getString("remaining"))
        assertEquals("151", candidate.getString("linkedPayments"))
    }

    @Test fun completeLinkedCollectionIsNotDebtEvenIfPaidFieldIsZero() {
        val result = LegacyCustomerDebtReconciliation.inspect(root(
            JSONArray().put(sale("مدفوع بالكامل")),
            JSONArray().put(payment("162"))))
        assertEquals(0, result.getInt("candidateCount"))
        assertEquals(0, result.getInt("conflictCount"))
    }

    @Test fun returnCanExtinguishUncollectedOnlineInvoice() {
        val returned = JSONObject().put("referenceType", "sale")
            .put("referenceId", "sale1").put("debtReduction", "162")
        val result = LegacyCustomerDebtReconciliation.inspect(root(
            JSONArray().put(sale("مسوّاة/مسترجعة")),
            JSONArray(), JSONArray().put(returned)))
        assertEquals(0, result.getInt("candidateCount"))
    }
}
