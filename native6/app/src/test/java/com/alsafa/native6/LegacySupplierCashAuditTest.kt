package com.alsafa.native6

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacySupplierCashAuditTest {
    @Test fun openingDebtSettlementMatchesNegativeCashOutflow() {
        val payload="""{"format":"alsafa-accounting-backup","formatVersion":2,"data":{
          "supplierLedger":[{"kind":"payment","supplierId":"supplier1","currency":"USD",
              "amount":520,"date":"2026-10-04","vault":"external"}],
          "cashTransactions":[{"type":"supplier-opening-debt-payment",
              "referenceType":"supplier","referenceId":"supplier1",
              "currency":"USD","amount":-520,"date":"2026-10-04","vault":"external"}]
        }}"""
        val report=LegacySupplierCashAudit.inspect(payload)
        assertEquals(1,report.getJSONArray("matched").length())
        assertEquals(0,report.getJSONArray("unmatchedLedgerIndexes").length())
        assertEquals("NONE",report.getString("posting"))
    }

    @Test fun wrongVaultCannotBeSilentlyMatched() {
        val payload="""{"format":"alsafa-accounting-backup","formatVersion":2,"data":{
          "supplierLedger":[{"kind":"payment","supplierId":"supplier1","currency":"USD",
              "amount":500,"date":"2026-10-15","vault":"shamcash"}],
          "cashTransactions":[{"type":"supplier-opening-debt-payment",
              "referenceType":"supplier","referenceId":"supplier1",
              "currency":"USD","amount":-500,"date":"2026-10-15","vault":"drawer"}]
        }}"""
        val report=LegacySupplierCashAudit.inspect(payload)
        assertEquals(1,report.getJSONArray("unmatchedLedgerIndexes").length())
    }
}
