package com.alsafa.native6

import org.json.JSONObject

/**
 * The October development sample is not a restore baseline. Always describe
 * the selected file, never assume it is the latest operating snapshot.
 */
object LegacyBackupFreshness {
    fun inspect(payload:String):JSONObject {
        return inspectRoot(JSONObject(payload))
    }
    fun inspectRoot(root:JSONObject):JSONObject {
        require(root.optString("format")=="alsafa-accounting-backup" &&
            root.optInt("formatVersion")==2) { "صيغة النسخة الاحتياطية غير مدعومة" }
        val data=root.getJSONObject("data")
        val collections=JSONObject()
        var records=0L
        for (key in data.keys()) {
            val rows=data.optJSONArray(key) ?: continue
            collections.put(key,rows.length())
            records=Math.addExact(records,rows.length().toLong())
        }
        return JSONObject()
            .put("exportedAt",root.optString("exportedAt","غير محدد"))
            .put("appVersion",root.optString("appVersion","غير محدد"))
            .put("formatVersion",root.getInt("formatVersion"))
            .put("collectionCounts",collections)
            .put("arrayRecordCount",records)
            .put("isFixedHistoricalSample",false)
            .put("policy","USE_SELECTED_BACKUP_ONLY")
            .put("warning","يجب اختيار أحدث نسخة عند الانتقال النهائي. لا تدمج نسخة قديمة مع نسخة جديدة.")
    }
    fun summary(payload:String):String = summaryRoot(JSONObject(payload))
    fun summaryRoot(root:JSONObject):String {
        val report=inspectRoot(root)
        val c=report.getJSONObject("collectionCounts")
        fun count(key:String)=c.optInt(key,0)
        return "النسخة المختارة: ${report.getString("exportedAt")}، إصدار التطبيق: ${report.getString("appVersion")}. " +
            "منتجات: ${count("products")}، قطع صيانة: ${count("maintenanceParts")}، " +
            "مبيعات: ${count("sales")}، مشتريات: ${count("purchases")}، " +
            "طلبات صيانة: ${count("repairs")}، موردون: ${count("suppliers")}، " +
            "حركات نقدية: ${count("cashTransactions")}، إقفالات: ${count("dailyClosings")}. " +
            report.getString("warning")
    }
}
