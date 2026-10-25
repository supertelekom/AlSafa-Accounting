package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject

/** Verify every archived collection against the original import receipt after restart. */
object LegacyArchiveIntegrity {
    fun inspect(store:Store):JSONObject {
        val receipt=store.rows("SELECT value FROM settings WHERE key=?","legacy_snapshot_import")
            .firstOrNull()?.get("value")
            ?: return JSONObject().put("available",false).put("complete",false)
                .put("missing",JSONArray()).put("posting","NONE")
        val metadata=JSONObject(receipt)
        val collections=metadata.optJSONObject("sourceCollections") ?: JSONObject()
        val differences=JSONArray()
        val counts=JSONObject()
        val excluded=setOf("products","maintenanceParts","suppliers","repairs")
        for(key in collections.keys()) {
            if(key in excluded) continue
            val expectedValue=collections.opt(key)
            val expected=when(expectedValue) {
                is Number -> expectedValue.toLong()
                is JSONObject -> expectedValue.optLong("count",-1L)
                else -> -1L
            }
            if(expected<0) continue
            val actual=store.scalar("SELECT count(*) FROM legacy_records WHERE category=?",key)
            counts.put(key,actual)
            if(expected!=actual) differences.put(JSONObject().put("section",key)
                .put("expected",expected).put("actual",actual))
        }
        val expectedProducts=metadata.optLong("products",-1L)+metadata.optLong("maintenanceParts",-1L)
        val actualProducts=store.scalar("SELECT count(*) FROM products")
        if(expectedProducts>=0 && expectedProducts!=actualProducts)
            differences.put(JSONObject().put("section","productsAndParts")
                .put("expected",expectedProducts).put("actual",actualProducts))
        val expectedRepairs=metadata.optLong("repairs",-1L)
        val actualRepairs=store.scalar("SELECT count(*) FROM repairs")
        if(expectedRepairs>=0 && expectedRepairs!=actualRepairs)
            differences.put(JSONObject().put("section","repairs")
                .put("expected",expectedRepairs).put("actual",actualRepairs))
        val expectedSuppliers=metadata.optLong("suppliers",-1L)
        val actualSuppliers=store.scalar("SELECT count(*) FROM suppliers")
        if(expectedSuppliers>=0 && expectedSuppliers!=actualSuppliers)
            differences.put(JSONObject().put("section","suppliers")
                .put("expected",expectedSuppliers).put("actual",actualSuppliers))
        return JSONObject().put("available",true).put("complete",differences.length()==0)
            .put("differences",differences).put("archivedCounts",counts)
            .put("sourceSha256",metadata.optString("sourceSha256"))
            .put("financialPostingStatus",metadata.optString("financialPostingStatus","NOT_POSTED"))
    }
}
