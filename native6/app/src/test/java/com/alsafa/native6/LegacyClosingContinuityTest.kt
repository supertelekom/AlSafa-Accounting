package com.alsafa.native6

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyClosingContinuityTest {
    private fun backup(rows:String)=
        """{"format":"alsafa-accounting-backup","formatVersion":2,"data":{"dailyClosings":[$rows]}}"""

    @Test fun exactDecimalCarryDoesNotCreateFalseDifference() {
        val payload=backup("""{"date":"2026-10-01","drawerActual":{"USD":0.3},"drawerTransfer":{"USD":0.1},"drawerNext":{"USD":0.2}}""")
        assertEquals(0,LegacyClosingContinuity.inspect(payload).getJSONArray("arithmeticMismatches").length())
    }

    @Test fun recordsMissingCurrencyAndDayGapWithoutPosting() {
        val payload=backup("""{"date":"2026-10-01","drawerNext":{"USD":20}},{"date":"2026-10-04","drawerActual":{"USD":25}}""")
        val result=LegacyClosingContinuity.inspect(payload)
        val transition=result.getJSONArray("transitions").getJSONObject(0)
        assertEquals(3L,transition.getLong("daysBetween"))
        assertEquals(2,transition.getJSONArray("missingCarryCurrencies").length())
        assertEquals("OPENING_BALANCE_NOT_VERIFIED",transition.getString("status"))
        assertEquals("NONE",result.getString("posting"))
    }

    @Test fun detectsIncorrectCarryForwardWithoutChangingData() {
        val payload=backup("""{"date":"2026-10-01","drawerActual":{"TRY":100},"drawerTransfer":{"TRY":40},"drawerNext":{"TRY":50}}""")
        val report=LegacyClosingContinuity.inspect(payload)
        assertEquals("10",report.getJSONArray("arithmeticMismatches").getJSONObject(0).getString("difference"))
    }
}
