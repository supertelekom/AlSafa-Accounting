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
                val row=a.optJSONObject(i)
                if(row==null) {invalid++;continue}
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
        data.optJSONObject("settings")?.let { settings ->
            val partners=settings.optJSONArray("partners")
            if(partners!=null) {
                var sum=BigDecimal.ZERO
                for(i in 0 until partners.length()) {
                    val partner=partners.optJSONObject(i) ?: continue
                    val share=partner.optString("share").toBigDecimalOrNull()
                    if(share!=null) sum=sum.add(share)
                    result += "شريك ${partner.optString("id")}: ${partner.optString("share")}%"
                }
                result += "مجموع نسب الشركاء: ${sum.toPlainString()}%"
            }
            result += "تسلسل الفواتير المحفوظ: " +
                settings.optString("invoiceSequenceHighWater","غير محدد")
        }
        // Withdrawal reports use the transaction's actual month, never the oldest
        // unpaid profit month. Group by partner and currency without FX guessing.
        data.optJSONArray("partnerWithdrawals")?.let { withdrawals ->
            val monthly=sortedMapOf<String,BigDecimal>()
            var malformed=0
            for(i in 0 until withdrawals.length()) {
                val row=withdrawals.optJSONObject(i)
                if(row==null) {malformed++;continue}
                val date=row.optString("date")
                val currency=row.optString("currency")
                val partner=row.optString("partnerId")
                val amount=row.optString("amount").toBigDecimalOrNull()
                if(!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) ||
                    currency !in listOf("USD","TRY","SYP") ||
                    partner.isBlank() || amount==null) {malformed++;continue}
                val key="${date.substring(0,7)} | $partner | $currency"
                monthly[key]=(monthly[key] ?: BigDecimal.ZERO).add(amount)
            }
            result += "سحوبات الشركاء بحسب شهر العملية الفعلي (دون تسوية أرباح):"
            monthly.forEach { (key,total) -> result += "$key: ${total.toPlainString()}" }
            if(malformed>0) result += "سحوبات غير صالحة للتجميع: $malformed"
        }
        // Invoice numbers are display identifiers, not safe primary keys.
        // The original data may contain duplicate numbers across different records.
        listOf("sales","repairs").forEach { key ->
            val a=data.optJSONArray(key) ?: return@forEach
            val numbers=linkedMapOf<String,Int>()
            for(i in 0 until a.length()) {
                val number=a.optJSONObject(i)?.optString("invoiceNo")?.trim().orEmpty()
                if(number.isNotEmpty()) numbers[number]=(numbers[number] ?: 0)+1
            }
            val collisions=numbers.filterValues { it>1 }
            result += "$key — أرقام فواتير مكررة: ${collisions.size}"
            if(collisions.isNotEmpty()) result +=
                "$key — أرقام تحتاج تدقيقًا: " + collisions.keys.take(20).joinToString("، ")
        }
        result += "تحذير: هذه إجماليات خام؛ قد تتداخل المقبوضات مع المبيعات والحركات النقدية. لا تُستخدم لإثبات الربح أو دين المورد أو رصيد الخزنة."
        result += "لم تُنشأ فواتير أو قيود مالية جديدة."
        return result.joinToString("\n")
    }
}
