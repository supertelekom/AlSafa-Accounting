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
        val linked=JSONArray()
        if(id.isNotBlank()) {
            val payments=store.legacyRows("payments")
            for(payment in payments) {
                val original=JSONObject(payment.getValue("payload"))
                val ref=original.optString("referenceId")
                val type=original.optString("referenceType")
                val expected=when(section) { "sales" -> "sale"; "repairs" -> "repair"; else -> "purchase" }
                if(ref==id && (type.equals(expected,ignoreCase=true) || type.equals(section,ignoreCase=true))) {
                    linked.put(JSONObject()
                        .put("archiveKey",payment.getValue("legacy_id"))
                        .put("record",original))
                }
            }
        }
        return JSONObject()
            .put("section",section)
            .put("archiveKey",archiveKey)
            .put("invoice",source)
            .put("linkedPayments",linked)
            .put("readOnly",true)
            .put("financialPosting","NONE")
            .put("paymentsAreEvidenceOnly",true)
            .put("isSimplifiedSale",section=="sales" && source.optString("customer").isBlank() && source.optString("customerName").isBlank() && source.optString("phone").isBlank())
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
            val financiallyValid=validId && validDate && validCurrency && validMoney
            if(!financiallyValid) invalidRecords++
            // Keep damaged source invoices visible for identification and repair.
            // Never include them in financial totals or permit settlement.
            val remaining=if(validMoney) total!!-paid!! else BigDecimal.ZERO
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
                .put("date",date).put("customer",original.optString("customer").ifBlank { original.optString("customerName") })
                .put("phone",original.optString("phone").ifBlank { original.optString("customerPhone").ifBlank { original.optString("supplierPhone") } })
                .put("supplierName",original.optString("supplierName"))
                .put("currency",currency).put("total",total?.toPlainString() ?: original.optString("total"))
                .put("paid",paid?.toPlainString() ?: original.optString("paid"))
                .put("outstanding",if(financiallyValid) remaining.toPlainString() else "")
                .put("financiallyValid",financiallyValid)
                .put("validationIssue",if(financiallyValid) "" else "تتطلب الفاتورة مراجعة معرفها أو تاريخها أو عملتها أو مبالغها")
                .put("paymentStatus",original.optString("paymentStatus"))
                .put("isSimplifiedSale",section=="sales" && original.optString("customer").isBlank() && original.optString("customerName").isBlank() && original.optString("phone").isBlank())
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
