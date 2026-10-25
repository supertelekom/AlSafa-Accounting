package com.alsafa.native6

import org.json.JSONObject
import java.math.BigDecimal

/** Arabic, human-readable review of legacy cash. Never writes to the database. */
object LegacyCashReportFormatter {
    fun format(plan: JSONObject): String = buildString {
        append("مراجعة الخزنات القديمة — قراءة فقط\n\n")
        val balances = plan.getJSONArray("balances")
        if (balances.length() == 0) append("لا توجد حركات نقدية في الأرشيف.\n")
        for (i in 0 until balances.length()) {
            val item = balances.getJSONObject(i)
            val old = item.getString("sourceSignedBalance")
            val activeMinor = item.getLong("activeMinorUnits")
            val active = BigDecimal.valueOf(activeMinor, 2).toPlainString()
            append(item.getString("vault")).append(" — ")
                .append(item.getString("currency")).append("\n")
                .append("رصيد الحركات القديمة: ").append(old).append("\n")
                .append("الرصيد التشغيلي الحالي: ").append(active).append("\n")
                .append("العد الفعلي: غير مؤكد\n\n")
        }
        val blockers = plan.getJSONArray("blockers")
        append("عوائق اعتماد الأرصدة: ").append(blockers.length()).append("\n")
        for (i in 0 until blockers.length()) {
            append("• ").append(blockers.getString(i)).append("\n")
        }
        append("\nلم تُرحّل أي مبالغ. يجب التحقق من الإقفالات والديون والعد الفعلي.")
    }
}
