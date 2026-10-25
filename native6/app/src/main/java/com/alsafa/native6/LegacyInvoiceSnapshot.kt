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
    private val allowed=setOf("sales","purchases")
    fun page(store:Store,section:String,from:String="",to:String="",term:String="",
             offset:Int=0,limit:Int=50):JSONObject {
        require(section in allowed){"نوع الفاتورة غير مدعوم"}
        require(offset>=0 && limit in 1..200)
        if(from.isNotBlank()) java.time.LocalDate.parse(from)
        if(to.isNotBlank()) java.time.LocalDate.parse(to)
        require(from.isBlank() || to.isBlank() || from<=to)
        val all=store.rows("SELECT legacy_id,payload FROM legacy_records WHERE category=? ORDER BY CAST(substr(legacy_id,1,instr(legacy_id,':')-1) AS INTEGER)",section)
        val matches=mutableListOf<JSONObject>()
        val outstanding=linkedMapOf<String,BigDecimal>()
        val query=term.trim()
        for(row in all) {
            val original=JSONObject(row.getValue("payload"))
            val date=original.optString("date")
            if(from.isNotBlank() && date<from) continue
            if(to.isNotBlank() && date>to) continue
            if(query.isNotBlank()) {
                val fields=listOf("invoiceNo","customer","phone","supplierName","supplierInvoice","notes")
                val found=fields.any { original.optString(it).contains(query,true) }
                val items=original.optJSONArray("items")
                val itemFound=items!=null && (0 until items.length()).any { index ->
                    val item=items.optJSONObject(index)
                    item!=null && listOf("name","productName","barcode","sku","model","productId").any {
                        item.optString(it).contains(query,true)
                    }
                }
                if(!found && !itemFound) continue
            }
            val currency=original.optString("currency")
            require(currency in setOf("USD","TRY","SYP")){"عملة فاتورة قديمة غير معروفة"}
            val total=original.optString("total","0").toBigDecimalOrNull()
            val paid=original.optString("paid","0").toBigDecimalOrNull()
            require(total!=null && paid!=null && total>=BigDecimal.ZERO &&
                paid>=BigDecimal.ZERO && paid<=total){"قيمة فاتورة قديمة غير صالحة"}
            val remaining=total-paid
            outstanding[currency]=(outstanding[currency] ?: BigDecimal.ZERO)+remaining
            matches.add(JSONObject().put("archiveKey",row.getValue("legacy_id"))
                .put("id",original.optString("id")).put("invoiceNo",original.optString("invoiceNo"))
                .put("date",date).put("customer",original.optString("customer"))
                .put("phone",original.optString("phone"))
                .put("supplierName",original.optString("supplierName"))
                .put("currency",currency).put("total",total.toPlainString())
                .put("paid",paid.toPlainString()).put("outstanding",remaining.toPlainString())
                .put("paymentStatus",original.optString("paymentStatus"))
                .put("readOnly",true))
        }
        val totals=JSONObject()
        outstanding.forEach { (currency,value)->totals.put(currency,value.toPlainString()) }
        val page=JSONArray()
        matches.drop(offset).take(limit).forEach { page.put(it) }
        return JSONObject().put("section",section).put("total",matches.size)
            .put("offset",offset).put("limit",limit).put("rows",page)
            .put("outstandingByCurrency",totals)
            .put("balanceBasis","ORIGINAL_INVOICE_CUMULATIVE_PAID")
            .put("financialPosting","NONE")
    }
}
