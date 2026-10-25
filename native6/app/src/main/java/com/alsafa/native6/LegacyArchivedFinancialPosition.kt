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
        val supplierOpeningBalances=linkedMapOf<String,BigDecimal>()
        val supplierNames=store.rows("SELECT id,name FROM suppliers")
            .associate { it.getValue("id") to it.getValue("name") }
        val seenLedgerIds=hashSetOf<String>()
        for(record in supplierLedger) {
            val row=JSONObject(record.getValue("payload"))
            val currency=row.optString("currency")
            val kind=row.optString("kind","unknown")
            val amount=row.optString("amount","0").toBigDecimalOrNull()
                ?: error("مبلغ غير صالح في دفتر الموردين")
            require(currency in currencies && amount.signum()>=0) {
                "قيد مورد بعملة أو مبلغ غير صالح"
            }
            val ledgerId=row.optString("id").trim()
            require(ledgerId.isNotEmpty() && seenLedgerIds.add(ledgerId)) {
                "معرف حركة مورد مفقود أو مكرر"
            }
            val supplierId=row.optString("supplierId").trim()
            require(supplierNames.containsKey(supplierId)) {
                "حركة مالية مرتبطة بمورد غير موجود"
            }
            val key="$kind|$currency"
            val previous=ledgerKinds.optString(key,"0").toBigDecimal()
            ledgerKinds.put(key,previous.add(amount).toPlainString())
            // The old supplier ledger contains independent opening debts.
            // These are NOT purchases and must never be merged into invoice paid.
            if(kind=="opening-debt" || kind=="payment") {
                val balanceKey="$supplierId|$currency"
                val signed=if(kind=="opening-debt") amount else amount.negate()
                supplierOpeningBalances[balanceKey]=
                    (supplierOpeningBalances[balanceKey] ?: BigDecimal.ZERO).add(signed)
            }
        }
        val supplierOpening=JSONArray()
        for((key,balance) in supplierOpeningBalances) {
            require(balance.signum()>=0) { "دفعات دفتر الموردين تتجاوز الدين الافتتاحي: $key" }
            val separator=key.lastIndexOf('|')
            val supplierId=key.substring(0,separator)
            val currency=key.substring(separator+1)
            supplierOpening.put(JSONObject()
                .put("supplierId",supplierId)
                .put("supplierName",supplierNames.getValue(supplierId))
                .put("currency",currency)
                .put("openingDebtLessLedgerPayments",balance.toPlainString())
                .put("requiresManualReconciliation",true))
        }
        // Returns are already reflected in some old invoice snapshots.
        // Report their cash/debt effects as evidence, NEVER subtract again.
        val returns=store.rows("SELECT payload FROM legacy_records WHERE category=?","returns")
        require(returns.size.toLong()==counts.optLong("returns",0)) {
            "المرتجعات التاريخية المؤرشفة غير مكتملة"
        }
        val returnTotals=JSONObject()
        val seenReturns=hashSetOf<String>()
        for(record in returns) {
            val row=JSONObject(record.getValue("payload"))
            val id=row.optString("id").trim()
            require(id.isNotEmpty() && seenReturns.add(id)) { "معرف مرتجع مكرر أو مفقود" }
            val currency=row.optString("currency")
            require(currency in currencies) { "عملة مرتجع غير مدعومة" }
            val referenceType=row.optString("referenceType").trim().lowercase()
            val referenceId=row.optString("referenceId").trim()
            require(referenceType in setOf("sale","sales","repair","repairs","purchase","purchases") && referenceId.isNotEmpty()) {
                "مرجع مرتجع غير معروف"
            }
            val fieldNames=listOf("refundAmount","debtReduction","netCreditAmount")
            for(field in fieldNames) {
                val amount=row.optString(field,"0").toBigDecimalOrNull()
                    ?: error("قيمة مرتجع غير صالحة: $field")
                require(amount.signum()>=0) { "قيمة مرتجع سالبة: $field" }
                val key="$currency|$field"
                returnTotals.put(key,
                    returnTotals.optString(key,"0").toBigDecimal().add(amount).toPlainString())
            }
        }
        return JSONObject()
            .put("returnEffectsAlreadyInSource",returnTotals)
            .put("supplierOpeningDebtBalances",supplierOpening)
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

    fun monthlyPartnerWithdrawals(store:Store,month:String):JSONObject {
        val period=java.time.YearMonth.parse(month)
        val meta=LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val expected=meta.getJSONObject("sourceCollections").optLong("partnerWithdrawals",0)
        val rows=store.rows("SELECT legacy_id,payload FROM legacy_records WHERE category=?",
            "partnerWithdrawals")
        require(rows.size.toLong()==expected) { "أرشيف سحوبات الشركاء غير مكتمل" }
        val totals=sortedMapOf<String,Long>()
        val entries=JSONArray()
        val seenIds=hashSetOf<String>()
        var excluded=0
        for(record in rows) {
            val item=JSONObject(record.getValue("payload"))
            val recordId=record.getValue("legacy_id")
            require(recordId.isNotBlank() && seenIds.add(recordId)) { "معرف سحب شريك مفقود أو مكرر" }
            val date=java.time.LocalDate.parse(item.getString("date").take(10))
            if(java.time.YearMonth.from(date)!=period) { excluded++;continue }
            val currency=item.optString("currency")
            require(currency in currencies) { "عملة سحب غير مدعومة" }
            require(item.optString("id").trim()==recordId) { "معرف السحب لا يطابق الأرشيف" }
            val partner=item.optString("partnerId").ifBlank {
                item.optString("partnerName").ifBlank {
                    item.optString("partner").ifBlank { item.optString("person","غير محدد") }
                }
            }
            val value=cents(item,"amount")
            val key="$partner|$currency"
            totals[key]=Math.addExact(totals[key] ?: 0,value)
            entries.put(JSONObject().put("id",record.getValue("legacy_id"))
                .put("date",date.toString()).put("partner",partner)
                .put("currency",currency).put("amount",amount(value)))
        }
        val sums=JSONObject()
        for((key,value) in totals) sums.put(key,amount(value))
        return JSONObject().put("month",period.toString())
            .put("withdrawals",entries).put("totalsByPartnerCurrency",sums)
            .put("excludedOtherMonths",excluded)
            .put("historicalProfitIncluded",false).put("posting","NONE")
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
        val ledger=report.getJSONObject("supplierLedgerByKindCurrency")
        if(ledger.length()>0) {
            lines.add("دفتر الموردين التاريخي (حسب نوع القيد؛ لا يُضاف إلى دين الفواتير):")
            for(key in ledger.keys()) lines.add("$key: ${ledger.getString(key)}")
        }
        val opening=report.getJSONArray("supplierOpeningDebtBalances")
        if(opening.length()>0) {
            lines.add("ديون الموردين الافتتاحية بعد دفعات دفتر الموردين (منفصلة عن فواتير المشتريات):")
            for(i in 0 until opening.length()) {
                val row=opening.getJSONObject(i)
                lines.add("${row.getString("supplierName")} / ${row.getString("currency")}: " +
                    row.getString("openingDebtLessLedgerPayments"))
            }
        }
        lines.add("أثر المرتجعات المحفوظ في الأرشيف: ${report.getJSONObject("returnEffectsAlreadyInSource")}")
        lines.add(report.getString("warning"))
        return lines.joinToString("\n")
    }
}
