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

}
