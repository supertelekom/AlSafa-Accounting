package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only access to complete imported legacy records.
 * Never treats archived invoice/payment/cash records as posted accounting entries.
 */
object LegacyArchiveBrowser {
    val sections=setOf("sales","purchases","payments","cashTransactions","dailyClosings",
        "partnerWithdrawals","supplierLedger","returns","expenses","auditLog",
        "productCostHistory","inventoryMovements","discountRules","loyaltyLedger",
        "smartReceipts","productDetailOptions","customerOrders","usedWatchTransactions",
        "inventorySessions","customerSessions","shiftHandovers")
    fun page(store:Store,section:String,from:String="",to:String="",query:String="",
             offset:Int=0,limit:Int=50):JSONObject {
        require(section in sections){"قسم الأرشيف غير معروف"}
        require(offset>=0 && limit in 1..200){"حجم الصفحة غير صالح"}
        require(query.length<=200){"عبارة البحث طويلة جداً"}
        if(from.isNotBlank()) java.time.LocalDate.parse(from)
        if(to.isNotBlank()) java.time.LocalDate.parse(to)
        require(from.isBlank() || to.isBlank() || from<=to){"نهاية الفترة تسبق بدايتها"}
        val rows=store.legacyRows(section)
        val filtered=rows.mapNotNull { row ->
            val data=JSONObject(row.getValue("payload"))
            val date=data.optString("date").take(10)
            if(from.isNotBlank() && (date.isBlank() || date<from)) return@mapNotNull null
            if(to.isNotBlank() && (date.isBlank() || date>to)) return@mapNotNull null
            if(query.isNotBlank()) {
                val term=query.trim()
                val fields=listOf("customer","phone","name","supplierName","invoiceNo",
                    "referenceId","referenceType","itemName","notes","kind","id",
                    "watch","problem","productName","partName","barcode")
                val direct=fields.any { data.optString(it).contains(term,ignoreCase=true) }
                val items=data.optJSONArray("items")
                val nested=items!=null && (0 until items.length()).any { index ->
                    val item=items.optJSONObject(index)
                    item!=null && listOf("name","productName","barcode","sku","model").any {
                        item.optString(it).contains(term,ignoreCase=true)
                    }
                }
                if(!direct && !nested) return@mapNotNull null
            }
            JSONObject().put("archiveKey",row.getValue("legacy_id"))
                .put("record",data)
        }
        val page=JSONArray()
        filtered.drop(offset).take(limit).forEach { page.put(it) }
        return JSONObject().put("section",section).put("total",filtered.size)
            .put("offset",offset).put("limit",limit).put("records",page)
            .put("readOnly",true).put("financialPosting","NONE")
    }

    fun get(store:Store,section:String,archiveKey:String):JSONObject? {
        require(section in sections)
        require(archiveKey.isNotBlank())
        return store.legacyRows(section).firstOrNull { it.getValue("legacy_id")==archiveKey }
            ?.get("payload")?.let(::JSONObject)
    }
}
