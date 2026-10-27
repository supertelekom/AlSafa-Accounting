package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LegacyCutoverWorksheetTest {
    private fun root(sales: JSONArray, purchases: JSONArray): JSONObject =
        JSONObject().put("format", "alsafa-accounting-backup")
            .put("formatVersion", 2)
            .put("data", JSONObject().put("sales", sales).put("purchases", purchases))

    private fun invoice(id: String, currency: String, total: String, paid: String) =
        JSONObject().put("id", id).put("currency", currency)
            .put("total", total).put("paid", paid)

    @Test fun separatesCurrenciesAndKeepsOnlyOpenInvoices() {
        val sales = JSONArray().put(invoice("a", "USD", "100", "60"))
            .put(invoice("b", "TRY", "250", "50"))
            .put(invoice("c", "USD", "10", "10"))
        val result = LegacyCutoverWorksheet.inspect(root(sales, JSONArray()))
        val balances = result.getJSONObject("balancesByCurrency").getJSONObject("sales")
        assertEquals("40", balances.getString("USD"))
        assertEquals("200", balances.getString("TRY"))
        assertEquals(2, result.getJSONObject("openInvoices").getJSONArray("sales").length())
        assertFalse(result.getBoolean("postsHistoricalTransactions"))
    }

    @Test fun rejectsOverpaymentWithoutChangingOriginal() {
        val bad = invoice("bad", "USD", "10", "20")
        val result = LegacyCutoverWorksheet.inspect(root(JSONArray().put(bad), JSONArray()))
        assertEquals(1, result.getInt("issueCount"))
        assertEquals("20", bad.getString("paid"))
    }

    @Test fun purchaseDebtIsIndependentFromCustomerReceivables() {
        val result = LegacyCutoverWorksheet.inspect(root(
            JSONArray().put(invoice("s", "USD", "40", "10")),
            JSONArray().put(invoice("p", "USD", "200", "100"))))
        assertEquals("30", result.getJSONObject("balancesByCurrency")
            .getJSONObject("sales").getString("USD"))
        assertEquals("100", result.getJSONObject("balancesByCurrency")
            .getJSONObject("purchases").getString("USD"))
    }
}
