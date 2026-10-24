package com.alsafa.native6

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyReturnLinkAuditTest {
    @Test fun returnsAreLinkedButNeverPosted() {
        val payload="""{"format":"alsafa-accounting-backup","formatVersion":2,"data":{
            "sales":[{"id":"s1"}],
            "returns":[{"referenceType":"sale","referenceId":"s1"},
                       {"referenceType":"sale","referenceId":"unknown"}]
        }}"""
        val report=LegacyReturnLinkAudit.inspect(payload)
        assertEquals(1,report.getJSONObject("linkedReturnIndexes").getJSONArray("sales:s1").length())
        assertEquals(1,report.getJSONArray("unresolvedReferenceRows").length())
        assertEquals("NONE",report.getString("posting"))
    }
}
