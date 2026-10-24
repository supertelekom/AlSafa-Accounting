package com.alsafa.native6

import org.json.JSONObject
import java.security.MessageDigest

/**
 * Immutable source inventory for a full-fidelity legacy restore.
 * A manifest is not a journal posting and never changes the active store.
 */
object LegacyRestoreManifest {
    private val collections = listOf(
        "products", "maintenanceParts", "sales", "repairs", "expenses",
        "payments", "partnerWithdrawals", "cashTransactions", "dailyClosings",
        "purchases", "suppliers", "supplierLedger", "returns",
        "usedWatchTransactions", "customerOrders", "auditLog",
        "discountRules", "loyaltyLedger", "productCostHistory",
        "smartReceipts", "productDetailOptions", "inventoryMovements",
        "inventorySessions", "customerSessions", "shiftHandovers"
    )

    fun inspect(payload: String): JSONObject {
        val root = JSONObject(payload)
        require(root.optString("format") == "alsafa-accounting-backup" &&
            root.optInt("formatVersion") >= 2) { "صيغة النسخة الاحتياطية غير مدعومة" }
        val data = root.getJSONObject("data")
        val counts = JSONObject()
        val missing = org.json.JSONArray()
        var records = 0L
        for (key in collections) {
            val array = data.optJSONArray(key)
            if (array == null) missing.put(key)
            counts.put(key, array?.length() ?: 0)
            records += array?.length() ?: 0
        }
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return JSONObject()
            .put("format", root.optString("format"))
            .put("formatVersion", root.optInt("formatVersion"))
            .put("appVersion", root.optString("appVersion"))
            .put("exportedAt", root.optString("exportedAt"))
            .put("sha256", sha256)
            .put("recordCount", records)
            .put("collections", counts)
            .put("missingCollections", missing)
            .put("financialPostingStatus", "NOT_POSTED")
            .put("readOnly", true)
    }

    fun summary(payload: String): String {
        val manifest = inspect(payload)
        val counts = manifest.getJSONObject("collections")
        return buildString {
            append("بصمة النسخة SHA-256: ").append(manifest.getString("sha256")).append('\n')
            append("الإصدار: ").append(manifest.optString("appVersion")).append('\n')
            append("عدد السجلات: ").append(manifest.getLong("recordCount")).append('\n')
            append("الأقسام الغائبة: ").append(manifest.getJSONArray("missingCollections").length()).append('\n')
            for (key in collections) {
                append(key).append(": ").append(counts.getInt(key)).append('\n')
            }
            append("الحالة: فحص فقط؛ لم تُرحّل الحركات المالية أو تُغيّر بيانات المحل")
        }
    }
}
