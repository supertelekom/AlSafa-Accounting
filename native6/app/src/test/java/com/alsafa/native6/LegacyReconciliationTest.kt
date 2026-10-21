package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyReconciliationTest {
    @Test fun differentiatesLinkedMissingAndUnlinkedCashTransactions() {
        val data=JSONObject()
            .put("sales",JSONArray().put(JSONObject().put("id","sale-1")))
            .put("repairs",JSONArray().put(JSONObject().put("id","repair-1")))
            .put("cashTransactions",JSONArray()
                .put(JSONObject().put("referenceType","sale").put("referenceId","sale-1").put("type","income"))
                .put(JSONObject().put("referenceType","sale").put("referenceId","sale-404").put("type","income"))
                .put(JSONObject().put("referenceType","currencyExchange").put("referenceId","exchange-1").put("type","currency-exchange-in"))
                .put(JSONObject().put("referenceType","").put("referenceId","").put("type","transfer-in")))
            .put("payments",JSONArray()
                .put(JSONObject().put("referenceType","repair").put("referenceId","repair-1")))
        val payload=JSONObject().put("format","alsafa-accounting-backup")
            .put("data",data).toString()
        val report=LegacyReconciliation.inspect(payload)
        assertTrue(report.contains("cashTransactions — مراجع صحيحة: 1، مفقودة: 1، أنواع تحتاج ربطًا: 1، بلا مرجع: 1"))
        assertTrue(report.contains("payments — مراجع صحيحة: 1، مفقودة: 0"))
        assertTrue(report.contains("sale / sale-404"))
    }
    @Test fun openingSupplierDebtIsSeparateFromPurchaseLiabilities() {
        val ledger=JSONArray()
            .put(JSONObject().put("supplierId","supplier-1").put("currency","USD")
                .put("kind","opening-debt").put("amount",1500))
            .put(JSONObject().put("supplierId","supplier-1").put("currency","USD")
                .put("kind","payment").put("amount",500))
        val data=JSONObject().put("supplierLedger",ledger)
            .put("purchases",JSONArray().put(JSONObject().put("id","purchase-1")
                .put("supplierId","supplier-1").put("total",250)))
        val payload=JSONObject().put("format","alsafa-accounting-backup")
            .put("data",data).toString()
        val report=LegacyReconciliation.inspect(payload)
        assertTrue(report.contains("supplier-1 | USD: افتتاحي 1500، مدفوع 500، الفرق 1000"))
        assertTrue(report.contains("لا تشمل فواتير الشراء"))
    }

    @Test fun rawCashMonthlyAuditNeverCombinesCurrencies() {
        val movements=JSONArray()
            .put(JSONObject().put("date","2026-09-30").put("currency","USD")
                .put("amount",10))
            .put(JSONObject().put("date","2026-10-01").put("currency","USD")
                .put("amount",20))
            .put(JSONObject().put("date","2026-10-02").put("currency","TRY")
                .put("amount",500))
        val payload=JSONObject().put("format","alsafa-accounting-backup")
            .put("data",JSONObject().put("cashTransactions",movements)).toString()
        val report=LegacyReconciliation.inspect(payload)
        assertTrue(report.contains("2026-09 | USD: 10 (1 حركة)"))
        assertTrue(report.contains("2026-10 | USD: 20 (1 حركة)"))
        assertTrue(report.contains("2026-10 | TRY: 500 (1 حركة)"))
    }

    @Test fun unpaidSalesAreReportedByCurrencyWithoutAssumingPaymentAllocation() {
        val data=JSONObject().put("sales",JSONArray()
            .put(JSONObject().put("currency","USD").put("total",100).put("paid",30))
            .put(JSONObject().put("currency","TRY").put("total",200).put("paid",50)))
        val report=LegacyReconciliation.inspect(JSONObject()
            .put("format","alsafa-accounting-backup").put("data",data).toString())
        assertTrue(report.contains("USD: 70"))
        assertTrue(report.contains("TRY: 150"))
    }

    @Test fun paymentsKeepCollectionCurrency() {
        val p=JSONObject().put("id","p1").put("referenceType","sale")
            .put("referenceId","s1").put("currency","USD")
            .put("amount",10).put("paymentCurrency","TRY").put("paymentAmount",420)
        val d=JSONObject().put("payments",JSONArray().put(p))
        val root=JSONObject().put("format","alsafa-accounting-backup").put("data",d)
        assertTrue(LegacyReconciliation.inspect(root.toString())
            .contains("sale | s1 | TRY: 420"))
    }

    @Test fun laterPaymentAuditDoesNotDoubleDeductInvoicePaid() {
        val sales=JSONArray().put(JSONObject().put("id","s1")
            .put("currency","USD").put("total",100).put("paid",60))
        val payments=JSONArray().put(JSONObject().put("id","p1")
            .put("referenceType","sale").put("referenceId","s1")
            .put("paymentCurrency","USD").put("paymentAmount",20))
        val data=JSONObject().put("sales",sales).put("payments",payments)
        val report=LegacyReconciliation.inspect(JSONObject()
            .put("format","alsafa-accounting-backup").put("data",data).toString())
        assertTrue(report.contains("USD: 40"))
        assertTrue(report.contains("نفس عملة الفاتورة 1"))
    }

    @Test fun partnerWithdrawalsStayInTheirActualMonth() {
        val w=JSONArray().put(JSONObject().put("date","2026-09-30")
            .put("partnerId","father").put("currency","USD").put("amount",12))
            .put(JSONObject().put("date","2026-10-01")
                .put("partnerId","father").put("currency","USD").put("amount",8))
        val root=JSONObject().put("format","alsafa-accounting-backup")
            .put("data",JSONObject().put("partnerWithdrawals",w))
        val report=LegacyReconciliation.inspect(root.toString())
        assertTrue(report.contains("2026-09 | father | USD: 12"))
        assertTrue(report.contains("2026-10 | father | USD: 8"))
    }

    @Test fun recoveredSupplierPaymentIsNotTreatedAsNewCashOutflow() {
        val ledger=JSONArray()
            .put(JSONObject().put("supplierId","supplier-1").put("currency","USD")
                .put("kind","opening-debt").put("amount",520))
            .put(JSONObject().put("supplierId","supplier-1").put("currency","USD")
                .put("kind","payment").put("amount",520)
                .put("recoveredFromCashTransactionId","cash-1"))
        val data=JSONObject().put("supplierLedger",ledger)
            .put("cashTransactions",JSONArray().put(JSONObject()
                .put("id","cash-1").put("currency","USD").put("amount",520)))
        val report=LegacyReconciliation.inspect(JSONObject()
            .put("format","alsafa-accounting-backup").put("data",data).toString())
        assertTrue(report.contains("مرتبطة 1، مرجع نقدي مفقود 0، مرجع مستعاد مكرر 0"))
        assertTrue(report.contains("يمنع إعادة خصمها من الخزنة"))
        assertTrue(report.contains("الفرق 0"))
    }
    @Test fun missingAndDuplicateRecoveredSupplierCashReferencesAreFlagged() {
        val ledger=JSONArray()
            .put(JSONObject().put("kind","payment")
                .put("recoveredFromCashTransactionId","cash-missing"))
            .put(JSONObject().put("kind","payment")
                .put("recoveredFromCashTransactionId","cash-missing"))
        val data=JSONObject().put("supplierLedger",ledger)
            .put("cashTransactions",JSONArray())
        val report=LegacyReconciliation.inspect(JSONObject()
            .put("format","alsafa-accounting-backup").put("data",data).toString())
        assertTrue(report.contains("مرتبطة 0، مرجع نقدي مفقود 2، مرجع مستعاد مكرر 1"))
    }

}
