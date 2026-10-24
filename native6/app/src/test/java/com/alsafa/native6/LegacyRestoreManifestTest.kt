package com.alsafa.native6

import org.junit.Assert.*
import org.junit.Test

class LegacyRestoreManifestTest {
    private val sections = listOf(
        "products", "maintenanceParts", "sales", "repairs", "expenses",
        "payments", "partnerWithdrawals", "cashTransactions", "dailyClosings",
        "purchases", "suppliers", "supplierLedger", "returns",
        "usedWatchTransactions", "customerOrders", "auditLog",
        "discountRules", "loyaltyLedger", "productCostHistory",
        "smartReceipts", "productDetailOptions", "inventoryMovements",
        "inventorySessions", "customerSessions", "shiftHandovers"
    )
    private fun backup(): String {
        val collections = sections.joinToString(",") { "\"$it\":[]"}
        return """{"format":"alsafa-accounting-backup","formatVersion":2,"data":{$collections}}"""
    }
    @Test fun manifestCountsAllCollections() {
        val manifest = LegacyRestoreManifest.inspect(backup())
        assertEquals(0L, manifest.getLong("recordCount"))
        assertEquals(64, manifest.getString("sha256").length)
        assertEquals("NOT_POSTED", manifest.getString("financialPostingStatus"))
    }
    @Test fun missingCollectionFailsClosed() {
        val payload = backup().replace("\"shiftHandovers\":[]", "\"other\":[]")
        assertThrows(IllegalArgumentException::class.java) {
            LegacyRestoreManifest.inspect(payload)
        }
    }
}
