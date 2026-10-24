package com.alsafa.native6

import org.junit.Test
import org.junit.Assert.assertEquals

class LegacyDebtReconciliationTest {
    @Test fun verifiesOutstanding() {
        val source = """{"format":"alsafa-accounting-backup","formatVersion":2,"data":{"sales":[{"currency":"USD","total":100,"paid":40}]}}"""
        val result = LegacyDebtReconciliation.inspect(source)
        assertEquals("60", result.getJSONObject("sales").getJSONObject("USD").getString("outstandingSnapshot"))
    }
    @Test fun separatesCurrencies() {
        val source = """{"format":"alsafa-accounting-backup","formatVersion":2,"data":{"purchases":[{"currency":"USD","total":20,"paid":5},{"currency":"TRY","total":100,"paid":0}]}}"""
        val totals = LegacyDebtReconciliation.inspect(source).getJSONObject("purchases")
        assertEquals("15", totals.getJSONObject("USD").getString("outstandingSnapshot"))
        assertEquals("100", totals.getJSONObject("TRY").getString("outstandingSnapshot"))
    }
}
