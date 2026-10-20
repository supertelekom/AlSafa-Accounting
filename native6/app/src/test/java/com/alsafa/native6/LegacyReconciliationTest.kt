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
}
