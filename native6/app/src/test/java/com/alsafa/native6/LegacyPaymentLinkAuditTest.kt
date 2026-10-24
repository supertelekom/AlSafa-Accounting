package com.alsafa.native6

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyPaymentLinkAuditTest {
    @Test fun paymentsAreEvidenceAndUnknownReferencesAreFlagged() {
        val payload="""{"format":"alsafa-accounting-backup","formatVersion":2,"data":{
            "sales":[{"id":"s1","total":100,"paid":20}],
            "payments":[{"referenceType":"sale","referenceId":"s1","amount":10},
                        {"referenceType":"sale","referenceId":"missing","amount":5}]
        }}"""
        val report=LegacyPaymentLinkAudit.inspect(payload)
        assertEquals("10",report.getJSONObject("linkedPaymentEvidence").getString("sales:s1"))
        assertEquals(1,report.getJSONArray("unresolvedReferenceRows").length())
        assertEquals("NONE",report.getString("posting"))
    }
}
