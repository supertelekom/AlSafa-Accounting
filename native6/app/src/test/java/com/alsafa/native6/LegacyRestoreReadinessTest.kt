package com.alsafa.native6

import org.junit.Assert.*
import org.junit.Test

class LegacyRestoreReadinessTest {
    @Test fun invalidPaymentReferenceBlocksFinancialActivation() {
        val payload="""{"format":"alsafa-accounting-backup","formatVersion":2,"data":{
            "products":[],"maintenanceParts":[],"suppliers":[],"repairs":[],
            "sales":[],"payments":[{"id":"p1","referenceType":"sale",
            "referenceId":"missing","amount":10,"currency":"USD",
            "paymentCurrency":"USD","paymentAmount":10}],
            "cashTransactions":[],"returns":[],"supplierLedger":[],"dailyClosings":[]
        }}"""
        val result=LegacyRestoreReadiness.inspect(payload)
        assertFalse(result.getBoolean("financialActivationPermitted"))
        assertTrue(result.getJSONArray("financialBlockers").toString().contains("PAYMENT_REFERENCE_UNKNOWN"))
    }
}
