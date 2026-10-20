package com.alsafa.native6

import org.json.JSONObject
import java.math.BigDecimal

/** Read-only financial inventory of the original backup; NEVER posts journal entries. */
object LegacyFinancialAudit {
    private val sections=listOf("sales","purchases","supplierLedger","payments",
        "partnerWithdrawals","cashTransactions","dailyClosings","expenses","returns")
    fun inspect(payload:String):String {
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup"){"صيغة النسخة غير مدعومة"}
        val data=root.getJSONObject("data")
        val result=mutableListOf("تدقيق مالي للنسخة القديمة — قراءة فقط",
            "الإصدار: ${root.optString("appVersion","غير معروف")}",
            "تاريخ التصدير: ${root.optString("exportedAt","غير معروف")}")
        sections.forEach { key ->
            val array=data.optJSONArray(key)
            result += "$key: ${array?.length() ?: "القسم غير موجود"}"
        }
        for(key in listOf("sales","purchases","payments","partnerWithdrawals")) {
            val a=data.optJSONArray(key) ?: continue
            val totals=linkedMapOf<String,BigDecimal>()
            var invalid=0
            for(i in 0 until a.length()) {
                val row=a.optJSONObject(i) ?: run {invalid++;continue}
                val currency=row.optString("currency")
                val field=when(key) {"sales","purchases"->"total";else->"amount"}
                val amount=row.optString(field).toBigDecimalOrNull()
                if(currency !in listOf("USD","TRY","SYP") || amount==null) {invalid++;continue}
                totals[currency]=(totals[currency] ?: BigDecimal.ZERO).add(amount)
            }
            result += "$key — الإجماليات حسب العملة (لا تعني الرصيد المستحق): " +
                totals.entries.joinToString("، "){"${it.key}: ${it.value.toPlainString()}"}
            if(invalid>0)result += "$key — سجلات تتطلب مراجعة: $invalid"
        }
        result += "تحذير: هذه إجماليات خام؛ قد تتداخل المقبوضات مع المبيعات والحركات النقدية. لا تُستخدم لإثبات الربح أو دين المورد أو رصيد الخزنة."
        result += "لم تُنشأ فواتير أو قيود مالية جديدة."
        return result.joinToString("\\n")
    }
}
