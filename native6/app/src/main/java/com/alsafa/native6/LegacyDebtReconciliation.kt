package com.alsafa.native6

import org.json.JSONObject
import java.math.BigDecimal

/**
 * Read-only invoice balance reconstruction from original snapshots.
 * Paid is the invoice's cumulative paid amount; separate payment rows are
 * evidence, NEVER additional money to add to paid.
 */
object LegacyDebtReconciliation {
    private val zero = BigDecimal.ZERO
    private fun amount(row:JSONObject, field:String):BigDecimal =
        row.optString(field,"0").toBigDecimalOrNull()
            ?: throw IllegalArgumentException("مبلغ غير صالح: $field")
    private fun byCurrency(data:JSONObject, section:String):JSONObject {
        val rows=data.optJSONArray(section) ?: return JSONObject()
        val totals=linkedMapOf<String,Triple<BigDecimal,BigDecimal,BigDecimal>>()
        for(i in 0 until rows.length()) {
            val row=rows.optJSONObject(i) ?: continue
            val currency=row.optString("currency")
            require(currency in setOf("USD","TRY","SYP")) { "$section[$i]: عملة غير مدعومة" }
            val total=amount(row,"total")
            val paid=amount(row,"paid")
            require(total>=zero && paid>=zero && paid<=total) {
                "$section[$i]: إجمالي أو تحصيل غير صالح"
            }
            val prev=totals[currency] ?: Triple(zero,zero,zero)
            totals[currency]=Triple(prev.first+total,prev.second+paid,
                prev.third+(total-paid))
        }
        val output=JSONObject()
        for((currency,values) in totals) output.put(currency,JSONObject()
            .put("invoiced",values.first.toPlainString())
            .put("paidSnapshot",values.second.toPlainString())
            .put("outstandingSnapshot",values.third.toPlainString()))
        return output
    }

    fun inspect(payload:String):JSONObject {
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup" &&
            root.optInt("formatVersion")>=2) { "صيغة النسخة غير مدعومة" }
        val data=root.getJSONObject("data")
        return JSONObject()
            .put("sales",byCurrency(data,"sales"))
            .put("purchases",byCurrency(data,"purchases"))
            .put("paymentRows",data.optJSONArray("payments")?.length() ?: 0)
            .put("supplierLedgerRows",data.optJSONArray("supplierLedger")?.length() ?: 0)
            .put("returnRows",data.optJSONArray("returns")?.length() ?: 0)
            .put("paymentReferenceVerification","PENDING")
            .put("posting","NONE")
            .put("warning","مبالغ الفواتير المتبقية مؤشرات أولية؛ يجب مطابقة المرتجعات ودفتر الموردين قبل إثبات الديون")
    }

    fun summary(payload:String):String {
        val report=inspect(payload)
        val lines=mutableListOf("مطابقة الديون التاريخية — للقراءة فقط")
        for(section in listOf("sales","purchases")) {
            val currencies=report.getJSONObject(section)
            for(currency in listOf("USD","TRY","SYP")) {
                if(!currencies.has(currency)) continue
                val values=currencies.getJSONObject(currency)
                lines += "$section / $currency: إجمالي ${values.getString("invoiced")}، محصل ${values.getString("paidSnapshot")}، متبقٍ ${values.getString("outstandingSnapshot")}"
            }
        }
        lines += "سجلات دفعات مستقلة: ${report.getInt("paymentRows")} (لا تُضاف إلى المدفوع بالفواتير)"
        lines += "قيود الموردين: ${report.getInt("supplierLedgerRows")} (تتطلب مطابقة مستقلة)"
        lines += "المرتجعات: ${report.getInt("returnRows")} (لا تُطرح مجددًا من الفاتورة قبل فحص أثرها المسجل)"
        lines += report.getString("warning")
        return lines.joinToString("\n")
    }
}
