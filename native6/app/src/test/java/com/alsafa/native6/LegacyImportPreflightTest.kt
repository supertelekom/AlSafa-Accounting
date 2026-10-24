package com.alsafa.native6

import org.junit.Assert.*
import org.junit.Test

class LegacyImportPreflightTest {
    @Test fun detectsInvalidInventory() {
        val payload = """{"format":"alsafa-accounting-backup","data":{"products":[{"id":"p1","name":"Watch","currency":"USD","stock":-1,"cost":"abc","salePrice":"10","barcode":"X"},{"id":"p2","name":"Watch","currency":"USD","stock":2,"barcode":"X"}],"maintenanceParts":[],"suppliers":[],"repairs":[]}}"""
        val issues=LegacyImportPreflight.inspect(payload)
        assertTrue(issues.any { it.contains("invalid stock") })
        assertTrue(issues.any { it.contains("invalid cost") })
        assertTrue(issues.any { it.contains("duplicate barcode") })
    }

    @Test fun detectsMissingSections() {
        val payload = """{"format":"alsafa-accounting-backup","data":{}}"""
        assertEquals(4, LegacyImportPreflight.inspect(payload).size)
    }
}
