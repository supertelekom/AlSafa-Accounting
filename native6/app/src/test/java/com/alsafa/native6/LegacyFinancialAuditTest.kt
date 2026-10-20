package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyFinancialAuditTest {
    @Test fun keepsUsdAndTrySeparateAndDoesNotPostAnyBalances() {
        val data=JSONObject()
            .put("sales",JSONArray()
                .put(JSONObject().put("currency","USD").put("total",12.5))
                .put(JSONObject().put("currency","TRY").put("total",490)))
            .put("purchases",JSONArray())
            .put("payments",JSONArray())
            .put("partnerWithdrawals",JSONArray())
        val payload=JSONObject().put("format","alsafa-accounting-backup")
            .put("appVersion","3.9.353").put("data",data).toString()
        val report=LegacyFinancialAudit.inspect(payload)
        assertTrue(report.contains("USD: 12.5"))
        assertTrue(report.contains("TRY: 490"))
        assertTrue(report.contains("لا تُستخدم لإثبات الربح"))
    }
}
