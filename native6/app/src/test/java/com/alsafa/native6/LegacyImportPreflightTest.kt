package com.alsafa.native6

import org.junit.Assert.*
import org.junit.Test

class LegacyImportPreflightTest {
    @Test fun detectsInvalidInventory() {
        val payload = """{"format":"alsafa-accounting-backup","formatVersion":2,"data":{"products":[{"id":"p1","name":"Watch","currency":"USD","stock":-1,"cost":"abc","salePrice":"10","barcode":"X"},{"id":"p2","name":"Watch","currency":"USD","stock":2,"barcode":"X"}],"maintenanceParts":[],"suppliers":[],"repairs":[]}}"""
        val issues=LegacyImportPreflight.inspect(payload)
        assertTrue(issues.any { it.contains("invalid stock") })
        assertTrue(issues.any { it.contains("invalid cost") })
        assertTrue(issues.any { it.contains("duplicate barcode") })
    }

    @Test fun detectsUnsupportedRepairState() {
        val payload = """{"format":"alsafa-accounting-backup","formatVersion":2,"data":{"products":[],"maintenanceParts":[],"suppliers":[],"repairs":[{"id":"r1","date":"2026-01-01","status":"unknown"}]}}"""
        assertTrue(LegacyImportPreflight.inspect(payload).any { it.contains("unsupported status") })
    }

    @Test fun detectsOrphanSupplierReferences() {
        val payload = """{"format":"alsafa-accounting-backup","formatVersion":2,"data":{"products":[],"maintenanceParts":[],"suppliers":[],"repairs":[],"purchases":[{"supplierId":"missing"}],"supplierLedger":[{"supplierId":"missing"}]}}"""
        val problems=LegacyImportPreflight.inspect(payload)
        assertTrue(problems.any { it.contains("purchases[0]: unknown supplier") })
        assertTrue(problems.any { it.contains("supplierLedger[0]: unknown supplier") })
    }

    @Test fun detectsOrphanPayment() {
        val payload = """{"format":"alsafa-accounting-backup","formatVersion":2,"data":{"products":[],"maintenanceParts":[],"suppliers":[],"repairs":[],"sales":[],"purchases":[],"payments":[{"referenceType":"sale","referenceId":"absent"}]}}"""
        assertTrue(LegacyImportPreflight.inspect(payload).any { it.contains("unknown sale reference") })
    }

    @Test fun detectsMissingSections() {
        val payload = """{"format":"alsafa-accounting-backup","formatVersion":2,"data":{}}"""
        assertEquals(4, LegacyImportPreflight.inspect(payload).size)
    }
}
