package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Financial position from the imported SQLite archive, not from a stale
 * development backup file. It is intentionally read-only.
 */
object LegacyArchivedFinancialPosition {
    private val currencies=listOf("USD","TRY","SYP")
    private fun cents(row:JSONObject,key:String):Long {
        val number=row.optString(key,"0").toBigDecimalOrNull()
            ?: error("قيمة غير صالحة في $key")
        require(number.signum()>=0) { "قيمة سالبة غير صالحة في $key" }
        return number.setScale(2,RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }
    private fun amount(minor:Long):String =
        BigDecimal.valueOf(minor,2).toPlainString()

    fun inspect(store:Store):JSONObject {
        val metadata=LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val counts=metadata.getJSONObject("sourceCollections")
        val result=JSONObject()
        for(section in listOf("sales","purchases")) {
            val rows=store.rows("SELECT legacy_id,payload FROM legacy_records WHERE category=?",
                section)
            require(rows.size.toLong()==counts.optLong(section,0)) {
                "سجلات $section غير مكتملة"
            }
            val ids=hashSetOf<String>()
            val totals=linkedMapOf<String,Long>()
            val paid=linkedMapOf<String,Long>()
            val outstanding=linkedMapOf<String,Long>()
            val entries=JSONArray()
            for(record in rows) {
                val row=JSONObject(record.getValue("payload"))
                val id=row.optString("id").trim()
                require(id.isNotEmpty() && ids.add(id)) { "معرف فاتورة مكرر في $section" }
                val currency=row.optString("currency")
                require(currency in currencies) { "عملة غير مدعومة في $section" }
                val invoice=cents(row,"total")
                val collected=cents(row,"paid")
                require(collected<=invoice) { "المدفوع يتجاوز الفاتورة $id" }
                val due=Math.subtractExact(invoice,collected)
                totals[currency]=Math.addExact(totals[currency] ?: 0,invoice)
                paid[currency]=Math.addExact(paid[currency] ?: 0,collected)
                outstanding[currency]=Math.addExact(outstanding[currency] ?: 0,due)
                if(due>0) entries.put(JSONObject().put("id",id)
                    .put("date",row.optString("date"))
                    .put("invoiceNo",row.optString("invoiceNo"))
                    .put("counterparty",if(section=="sales")row.optString("customer")
                        else row.optString("supplierName"))
                    .put("currency",currency)
                    .put("outstanding",amount(due)))
            }
            val perCurrency=JSONObject()
            for(currency in currencies) perCurrency.put(currency,JSONObject()
                .put("invoiced",amount(totals[currency] ?: 0))
                .put("paidSnapshot",amount(paid[currency] ?: 0))
                .put("outstandingSnapshot",amount(outstanding[currency] ?: 0)))
            result.put(section,JSONObject()
                .put("totals",perCurrency)
                .put("outstandingInvoices",entries)
                .put("invoiceCount",rows.size))
        }
        // Supplier ledger is separate evidence: never add it to invoice dues.
        val supplierLedger=store.rows(
            "SELECT payload FROM legacy_records WHERE category=?", "supplierLedger")
        require(supplierLedger.size.toLong()==counts.optLong("supplierLedger",0)) {
            "دفتر الموردين المؤرشف غير مكتمل"
        }
        val ledgerKinds=JSONObject()
        for(record in supplierLedger) {
            val row=JSONObject(record.getValue("payload"))
            val currency=row.optString("currency")
            val kind=row.optString("kind","unknown")
            val amount=row.optString("amount","0").toBigDecimalOrNull()
                ?: error("مبلغ غير صالح في دفتر الموردين")
            require(currency in currencies && amount.signum()>=0) {
                "قيد مورد بعملة أو مبلغ غير صالح"
            }
            val key="$kind|$currency"
            val previous=ledgerKinds.optString(key,"0").toBigDecimal()
            ledgerKinds.put(key,previous.add(amount).toPlainString())
        }
        return JSONObject()
            .put("supplierLedgerByKindCurrency",ledgerKinds)
            .put("sourceSha256",metadata.optString("sourceSha256"))
            .put("sourceExportedAt",metadata.optString("sourceExportedAt"))
            .put("positions",result)
            .put("paymentRowsNotAddedAgain",counts.optLong("payments",0))
            .put("supplierLedgerRowsRequireReconciliation",counts.optLong("supplierLedger",0))
            .put("returnsRequireReconciliation",counts.optLong("returns",0))
            .put("financialActivationPermitted",false)
            .put("posting","NONE")
            .put("warning","هذه ذمم الفواتير المؤرشفة وليست قيوداً نشطة؛ يجب تدقيق المرتجعات ودفتر الموردين والمدفوعات قبل التفعيل.")
    }

    fun summary(store:Store):String {
        val report=inspect(store)
        val positions=report.getJSONObject("positions")
        val lines=mutableListOf("مطابقة ذمم النسخة المستوردة من SQLite — قراءة فقط")
        for(section in listOf("sales","purchases")) {
            val totals=positions.getJSONObject(section).getJSONObject("totals")
            for(currency in currencies) {
                val v=totals.getJSONObject(currency)
                lines.add("$section / $currency: الإجمالي ${v.getString("invoiced")}، " +
                    "المدفوع ${v.getString("paidSnapshot")}، المتبقي ${v.getString("outstandingSnapshot")}")
            }
        }
        lines.add(report.getString("warning"))
        return lines.joinToString("\n")
    }
}
