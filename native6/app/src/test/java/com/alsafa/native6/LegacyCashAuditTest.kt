package com.alsafa.native6

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyCashAuditTest {
    @Test fun movementsAreSeparatedByVaultAndCurrency() {
        val backup = """{"data":{"cashTransactions":[{"vault":"drawer","currency":"USD","type":"deposit","amount":10},{"vault":"drawer","currency":"TRY","type":"deposit","amount":20},{"vault":"external","currency":"USD","type":"deposit","amount":30}]}}"""
        val result=LegacyCashAudit.inspect(backup)
        val totals=result.getJSONObject("totals")
        assertEquals("10",totals.getString("drawer|USD|deposit"))
        assertEquals("20",totals.getString("drawer|TRY|deposit"))
        assertEquals("30",totals.getString("external|USD|deposit"))
        assertEquals("NONE",result.getString("posting"))
    }
}
