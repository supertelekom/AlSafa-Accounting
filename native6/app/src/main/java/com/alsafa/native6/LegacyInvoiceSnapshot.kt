package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Searchable historical invoice and debt snapshot after import.
 * Invoice paid is cumulative. Payment rows and cash entries are corroborating
 * evidence only; they must never be added to the invoice's paid amount again.
 */
object LegacyInvoiceSnapshot {
    private val allowed=setOf("sales","purchases","repairs")
    private val invoiceSections=setOf("sales","purchases")

    /**
     * Returns the complete source invoice, without posting another sale, purchase,
     * stock change or receipt. Historical payment rows are evidence, not additional
     * paid amounts. Caller must reconcile before enabling debt settlement.
     */
    fun detail(store:Store,section:String,archiveKey:String):JSONObject? {
        require(section in allowed){"نوع الفاتورة غير مدعوم"}
        require(archiveKey.isNotBlank()){"معرف الفاتورة مطلوب"}
        LegacyArchivedDebtReport.verifyArchiveCounts(store)
        // Legacy invoices can include embedded photos: use bounded reads.
        val row=store.legacyRecord(section,archiveKey) ?: return null
        val source=JSONObject(row.getValue("payload"))
        val id=source.optString("id").trim()
        require(id.isNotEmpty()) { "معرف الفاتورة التاريخية مفقود" }
        require(archiveKey.substringAfter(":",missingDelimiterValue="")==id) {
            "معرف الفاتورة لا يطابق مفتاح الأرشيف"
        }
        if(section in invoiceSections) {
            val total=source.optString("total","0").toBigDecimalOrNull()
            val paid=source.optString("paid","0").toBigDecimalOrNull()
            require(total!=null && paid!=null && total>=BigDecimal.ZERO &&
                paid>=BigDecimal.ZERO && paid<=total) { "مبالغ الفاتورة التاريخية غير متطابقة" }
            require(source.optString("currency") in setOf("USD","TRY","SYP")) {
                "عملة الفاتورة التاريخية غير مدعومة"
            }
        }
        // A restored invoice must never silently share an identity with a posted
        // native invoice. This check is read-only and creates no accounting entries.
        if(section in invoiceSections || section=="repairs") {
            val nativeCollision=store.rows("SELECT id FROM invoices WHERE id=? LIMIT 1",id)
            require(nativeCollision.isEmpty()) {
                "معرف الفاتورة المستعادة مستخدم في قاعدة الفواتير النشطة"
            }
            val postedCollision=store.rows("SELECT id FROM events WHERE id=? LIMIT 1",id)
            require(postedCollision.isEmpty()) {
                "معرف الفاتورة المستعادة مرتبط بحركة مالية نشطة"
            }
        }
        val linked=JSONArray()
        val seenPaymentIds=HashSet<String>()
        if(id.isNotBlank()) {
            val payments=store.legacyRows("payments")
            for(payment in payments) {
                val original=JSONObject(payment.getValue("payload"))
                val ref=original.optString("referenceId")
                val type=original.optString("referenceType")
                val expected=when(section) { "sales" -> "sale"; "repairs" -> "repair"; else -> "purchase" }
                if(ref==id && (type.equals(expected,ignoreCase=true) || type.equals(section,ignoreCase=true))) {
                    val paymentId=original.optString("id").trim()
                    require(paymentId.isNotEmpty() && seenPaymentIds.add(paymentId)) {
                        "معرف قبض تاريخي مفقود أو مكرر للفاتورة"
                    }
                    linked.put(JSONObject()
                        .put("archiveKey",payment.getValue("legacy_id"))
                        .put("record",original))
                }
            }
        }
        // Returns are historical records linked to the original invoice. Keep their
        // source amounts and dates intact; never post them again during display.
        val linkedReturns=JSONArray()
        val seenReturnIds=HashSet<String>()
        if(section=="sales" || section=="repairs") {
            for(returnRow in store.legacyRows("returns")) {
                val original=JSONObject(returnRow.getValue("payload"))
                val type=original.optString("referenceType")
                val expected=if(section=="sales") "sale" else "repair"
                if(original.optString("referenceId")==id &&
                    (type.equals(expected,ignoreCase=true) || type.equals(section,ignoreCase=true))) {
                    val returnId=original.optString("id").trim()
                    require(returnId.isNotEmpty() && seenReturnIds.add(returnId)) {
                        "معرف مرتجع تاريخي مفقود أو مكرر للفاتورة"
                    }
                    linkedReturns.put(JSONObject()
                        .put("archiveKey",returnRow.getValue("legacy_id"))
                        .put("record",original))
                }
            }
        }
        return JSONObject()
            .put("section",section)
            .put("archiveKey",archiveKey)
            .put("invoice",source)
            .put("linkedPayments",linked)
            .put("linkedReturns",linkedReturns)
            .put("readOnly",true)
            .put("financialPosting","NONE")
            .put("paymentsAreEvidenceOnly",true)
            .put("isSimplifiedSale",section=="sales" && source.optString("customer").isBlank() && source.optString("phone").isBlank())
            .put("paidSnapshotIsCumulative",true)
    }
    fun page(store:Store,section:String,from:String="",to:String="",term:String="",
             offset:Int=0,limit:Int=50):JSONObject {
        require(section in invoiceSections){"نوع الفاتورة التاريخية غير مدعوم"}
        require(offset>=0 && limit in 1..200)
        if(from.isNotBlank()) java.time.LocalDate.parse(from)
        if(to.isNotBlank()) java.time.LocalDate.parse(to)
        require(from.isBlank() || to.isBlank() || from<=to)
        LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val all=store.legacyRows(section)
        val settlementIndex=if(section=="sales") store.legacySettlementIndex() else null
        val matches=mutableListOf<JSONObject>()
        val seenInvoiceIds=hashSetOf<String>()
        val outstanding=linkedMapOf<String,BigDecimal>()
        val query=SaleInvoiceFilter.normalize(term)
        require(query.length<=200) { "عبارة البحث طويلة جداً" }
        var invalidRecords=0
        for(row in all) {
            val original=JSONObject(row.getValue("payload"))
            val invoiceId=original.optString("id").trim()
            val date=original.optString("date").take(10)
            val currency=original.optString("currency","")
            val total=original.optString("total","").toBigDecimalOrNull()
            val paid=original.optString("paid","").toBigDecimalOrNull()
            val validId=invoiceId.isNotEmpty() &&
                row.getValue("legacy_id").substringAfter(":",missingDelimiterValue="")==invoiceId &&
                seenInvoiceIds.add(invoiceId)
            val validDate=runCatching { java.time.LocalDate.parse(date) }.isSuccess
            val validCurrency=currency in setOf("USD","TRY","SYP")
            val validMoney=total!=null && paid!=null && total>=BigDecimal.ZERO &&
                paid>=BigDecimal.ZERO && paid<=total
            // For sales, historical returns and separate collections can change the
            // actual receivable. Never present total-minus-paid as the reconciled debt.
            val reconciled=if(section=="sales" && validId && validDate && validCurrency && validMoney)
                runCatching { store.reconciledLegacySaleDue(original,settlementIndex!!) }.getOrNull()
            else null
            val financiallyValid=validId && validDate && validCurrency && validMoney &&
                (section!="sales" || reconciled!=null)
            if(!financiallyValid) invalidRecords++
            // Unreconciled historical sales stay visible but never enter debt totals.
            val remaining=if(section=="sales") {
                if(reconciled==null) BigDecimal.ZERO else
                    BigDecimal.valueOf(reconciled,2)
            } else if(validMoney) total!!-paid!! else BigDecimal.ZERO
            if(from.isNotBlank() && (date.isBlank() || date<from)) continue
            if(to.isNotBlank() && (date.isBlank() || date>to)) continue
            if(query.isNotBlank()) {
                val fields=if(section=="sales") listOf("invoiceNo","customer","phone","notes","id") else listOf("supplierInvoice","supplierName","supplierId","notes","id")
                val found=fields.any { SaleInvoiceFilter.normalize(original.optString(it)).contains(query) }
                val items=original.optJSONArray("items")
                val itemFound=items!=null && (0 until items.length()).any { index ->
                    val item=items.optJSONObject(index)
                    item!=null && listOf("name","productName","barcode","sku","model","productId").any {
                        SaleInvoiceFilter.normalize(item.optString(it)).contains(query)
                    }
                }
                if(!found && !itemFound) continue
            }
            if(financiallyValid) outstanding[currency]=(outstanding[currency] ?: BigDecimal.ZERO)+remaining
            matches.add(JSONObject().put("archiveKey",row.getValue("legacy_id"))
                .put("id",original.optString("id"))
                .put("invoiceNo",original.optString("invoiceNo"))
                .put("supplierInvoice",original.optString("supplierInvoice"))
                .put("date",date).put("customer",original.optString("customer"))
                .put("phone",original.optString("phone"))
                .put("supplierName",original.optString("supplierName"))
                .put("currency",currency).put("total",total?.toPlainString() ?: original.optString("total"))
                .put("paid",paid?.toPlainString() ?: original.optString("paid"))
                .put("outstanding",if(financiallyValid) remaining.toPlainString() else "")
                .put("financiallyValid",financiallyValid)
                .put("validationIssue",if(financiallyValid) "" else "تتطلب الفاتورة مراجعة معرفها أو تاريخها أو عملتها أو تحصيلاتها أو مرتجعاتها")
                .put("paymentStatus",original.optString("paymentStatus"))
                .put("isSimplifiedSale",section=="sales" && original.optString("customer").isBlank() && original.optString("phone").isBlank())
                .put("readOnly",true))
        }
        val totals=JSONObject()
        outstanding.forEach { (currency,value)->totals.put(currency,value.toPlainString()) }
        val page=JSONArray()
        // Show the newest transaction day first, preserving a stable order for pagination.
        matches.sortWith(compareByDescending<JSONObject> { it.optString("date") }
            .thenByDescending { if(section=="purchases") it.optString("supplierInvoice") else it.optString("invoiceNo") }
            .thenByDescending { it.optString("id") })
        matches.drop(offset).take(limit).forEach { page.put(it) }
        return JSONObject().put("section",section).put("total",matches.size)
            .put("invalidSourceRecords",invalidRecords)
            .put("offset",offset).put("limit",limit).put("rows",page)
            .put("outstandingByCurrency",totals)
            .put("balanceBasis","ORIGINAL_INVOICE_CUMULATIVE_PAID")
            .put("financialPosting","NONE")
    }
}
