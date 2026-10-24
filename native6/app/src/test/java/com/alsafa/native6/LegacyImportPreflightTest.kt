package com.alsafa.native6

import org.junit.Assert.*
import org.junit.Test

class LegacyImportPreflightTest {
    @Test fun detectsMissingSections() {
        val payload = """{"format":"alsafa-accounting-backup","data":{}}"""
        assertEquals(4, LegacyImportPreflight.inspect(payload).size)
    }
}
