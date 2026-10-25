package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Reconciled opening-position worksheet, NOT a journal posting.
 * Computes receivables/payables from invoice cumulative paid snapshots,
 * and cash from signed cash transactions. Never adds payment rows twice.
 * All currencies are separate; historical partner drawings stay dated.
 */
object LegacyFinancialOpeningReport {
    private val currencies=listOf("USD","TRY","SYP")
    private fun money(row:JSONObject,field:String):BigDecimal {
        val value=row.optString(field,"0").toBigDecimalOrNull()
            ?: throw IllegalArgumentException("مبلغ غير صالح في $field")
        require(value>=BigDecimal.ZERO) { "مبلغ سالب غير صالح في $field" }
        return value.setScale(2,RoundingMode.HALF_UP)
    }
    private fun amountText(value:BigDecimal)=value.setScale(2,RoundingMode.HALF_UP).toPlainString()

    private fun invoiceBalances(data:JSONObject,section:String):JSONObject {
        val invoices=data.optJSONArray(section) ?: JSONArray()
        val byCurrency=linkedMapOf<String,MutableList<JSONObject>>()
        val ids=hashSetOf<String>()
        for(i in 0 until invoices.length()) {
            val row=invoices.getJSONObject(i)
            val id=row.optString("id").trim()
            require(id.isNotEmpty() && ids.add(id)) { "معرف فاتورة مكرر أو مفقود في $section" }
            val currency=row.optString("currency")
            require(currency in currencies) { "عملة فاتورة غير معروفة: $currency" }
            val total=money(row,"total")
            val paid=money(row,"paid")
            require(paid<=total) { "المدفوع يتجاوز إجمالي الفاتورة: $id" }
            val due=total-paid
            byCurrency.getOrPut(currency){mutableListOf()}.add(JSONObject()
                .put("id",id)
                .put("date",row.optString("date"))
                .put("invoiceNo",row.optString("invoiceNo"))
                .put("counterparty",if(section=="purchases")row.optString("supplierName") else row.optString("customer"))
                .put("total",amountText(total))
                .put("paidSnapshot",amountText(paid))
                .put("outstandingSnapshot",amountText(due))
                .put("source","INVOICE_CUMULATIVE_PAID"))
        }
        val output=JSONObject()
        for(currency in currencies) {
            val rows=byCurrency[currency].orEmpty()
            val due=rows.fold(BigDecimal.ZERO){a,r->a+r.getString("outstandingSnapshot").toBigDecimal()}
            val total=rows.fold(BigDecimal.ZERO){a,r->a+r.getString("total").toBigDecimal()}
            val paid=rows.fold(BigDecimal.ZERO){a,r->a+r.getString("paidSnapshot").toBigDecimal()}
            output.put(currency,JSONObject()
                .put("invoiceCount",rows.size)
                .put("invoiced",amountText(total))
                .put("paidSnapshot",amountText(paid))
                .put("outstandingSnapshot",amountText(due))
                .put("invoices",JSONArray(rows)))
        }
        return output
    }

    fun inspect(payload:String):JSONObject {
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup" &&
            root.optInt("formatVersion")==2) { "صيغة النسخة القديمة غير مدعومة" }
        val data=root.getJSONObject("data")
        val debt=invoiceBalances(data,"sales")
        val payable=invoiceBalances(data,"purchases")
        val cash=LegacyVaultSnapshot.inspect(payload)
        val readiness=LegacyRestoreReadiness.inspect(payload)
        return JSONObject()
            .put("exportedAt",root.optString("exportedAt"))
            .put("sourceAppVersion",root.optString("appVersion"))
            .put("customerReceivables",debt)
            .put("supplierPayablesFromPurchases",payable)
            .put("cashMovementBalances",cash.getJSONObject("balances"))
            .put("cashHasUnverifiedPhysicalCounts",true)
            .put("independentPaymentRows",data.optJSONArray("payments")?.length() ?: 0)
            .put("supplierLedgerRows",data.optJSONArray("supplierLedger")?.length() ?: 0)
            .put("returnsRows",data.optJSONArray("returns")?.length() ?: 0)
            .put("partnerWithdrawalRows",data.optJSONArray("partnerWithdrawals")?.length() ?: 0)
            .put("financialBlockers",readiness.getJSONArray("financialBlockers"))
            .put("financialActivationPermitted",false)
            .put("posting","NONE")
            .put("warning","أرصدة تقديرية للمطابقة فقط. لا تجمع الدفعات المستقلة مع المدفوع التراكمي؛ ولا تعتمد النقد دون عدّ الخزنات ومطابقة المرتجعات ودفتر الموردين.")
    }

    fun summary(payload:String):String {
        val report=inspect(payload)
        val lines=mutableListOf("ورقة مطابقة الوضع المالي عند تاريخ النسخة: ${report.optString("exportedAt")}")
        for(currency in currencies) {
            val customer=report.getJSONObject("customerReceivables").getJSONObject(currency)
            val supplier=report.getJSONObject("supplierPayablesFromPurchases").getJSONObject(currency)
            lines.add("$currency — ذمم العملاء: ${customer.getString("outstandingSnapshot")}، ديون المشتريات: ${supplier.getString("outstandingSnapshot")}")
        }
        lines.add("حركات نقدية مجمعة: ${report.getJSONObject("cashMovementBalances")}")
        lines.add("عوائق التفعيل المالي: ${report.getJSONArray("financialBlockers").length()}")
        lines.add(report.getString("warning"))
        return lines.joinToString("\n")
    }
}
