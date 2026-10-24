package com.alsafa.native6

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacySupplierDebtAuditTest {
    @Test fun separatesOpeningDebtFromUnpaidPurchases() {
        val source = """{"format":"alsafa-accounting-backup","formatVersion":2,"data":{"suppliers":[{"id":"s1","name":"Vendor"}],"purchases":[{"supplierId":"s1","currency":"USD","total":200,"paid":50}],"supplierLedger":[{"supplierId":"s1","currency":"USD","kind":"opening-debt","amount":500},{"supplierId":"s1","currency":"USD","kind":"payment","amount":100}]}}"""
        val report=LegacySupplierDebtAudit.inspect(source)
        val row=report.getJSONArray("suppliers").getJSONObject(0)
        assertEquals("150",row.getString("unpaidPurchases"))
        assertEquals("500",row.getString("openingDebt"))
        assertEquals("100",row.getString("ledgerPayments"))
        assertEquals("550",row.getString("provisionalBalance"))
        assertEquals("NONE",report.getString("posting"))
    }
}
