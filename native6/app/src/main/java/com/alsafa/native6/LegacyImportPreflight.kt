package com.alsafa.native6

import org.json.JSONObject
import java.time.LocalDate

/** Read-only validation; never mutates the active store. */
object LegacyImportPreflight {
    fun inspect(payload:String):List<String> {
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup")
        val data=root.getJSONObject("data")
        require(root.optInt("formatVersion")>=2){"Unsupported legacy backup version"}
        val problems=mutableListOf<String>()
        val ids=HashSet<String>()
        val barcodes=HashSet<String>()
        for(section in listOf("products","maintenanceParts","suppliers","repairs")) {
            val items=data.optJSONArray(section)
            if(items==null) {
                problems.add("Missing section: $section")
                continue
            }
            for(i in 0 until items.length()) {
                val item=items.optJSONObject(i)
                if(item==null) { problems.add("$section[$i]: invalid record");continue }
                val id=item.optString("id").trim()
                if(id.isEmpty() || !ids.add(id)) problems.add("$section[$i]: missing or duplicate ID")
                if(section=="products" || section=="maintenanceParts") {
                    val barcode=item.optString("barcode").trim()
                    if(barcode.isNotEmpty() && !barcodes.add(barcode))
                        problems.add("$section[$i]: duplicate barcode")
                    if(item.optString("currency") !in setOf("USD","TRY","SYP"))
                        problems.add("$section[$i]: unsupported currency")
                    val qty=item.optString("stock").toLongOrNull()
                    if(qty==null || qty<0) problems.add("$section[$i]: invalid stock")
                    if(item.optString("name").isBlank())
                        problems.add("$section[$i]: missing product name")
                    for(field in listOf("cost","salePrice")) {
                        val amount=item.optString(field,"0").toBigDecimalOrNull()
                        if(amount==null || amount.signum()<0)
                            problems.add("$section[$i]: invalid $field")
                    }
                }
                if(section=="repairs") {
                    val date=item.optString("date")
                    val valid=runCatching { !LocalDate.parse(date).isAfter(LocalDate.now()) }.getOrDefault(false)
                    if(!valid) problems.add("repairs[$i]: invalid intake date")
                    val status=item.optString("status")
                    if(status.isNotBlank() && status !in setOf(
                        "تم التسليم","delivered","قيد الصيانة","in_progress",
                        "جاهزة","جاهز","جاهز للاستلام","بانتظار التسليم","ready",
                        "مستلمة","تم الاستلام","قيد الانتظار","received"))
                        problems.add("repairs[$i]: unsupported status")
                }
            }
        }
        val purchaseIds=HashSet<String>()
        data.optJSONArray("purchases")?.let { purchases ->
            for(i in 0 until purchases.length()) {
                val id=purchases.optJSONObject(i)?.optString("id").orEmpty().trim()
                if(id.isNotEmpty() && !purchaseIds.add(id))
                    problems.add("purchases[$i]: duplicate purchase ID")
            }
        }
        val saleIds=HashSet<String>()
        data.optJSONArray("sales")?.let { sales ->
            for(i in 0 until sales.length()) {
                val id=sales.optJSONObject(i)?.optString("id").orEmpty().trim()
                if(id.isNotEmpty() && !saleIds.add(id))
                    problems.add("sales[$i]: duplicate sale ID")
            }
        }
        data.optJSONArray("returns")?.let { returns ->
            for(i in 0 until returns.length()) {
                val row=returns.optJSONObject(i) ?: continue
                val sale=row.optString("saleId").trim()
                if(sale.isNotEmpty() && sale !in saleIds)
                    problems.add("returns[$i]: unknown sale reference")
            }
        }
        data.optJSONArray("cashTransactions")?.let { transactions ->
            val seen=HashSet<String>()
            for(i in 0 until transactions.length()) {
                val id=transactions.optJSONObject(i)?.optString("id").orEmpty().trim()
                if(id.isNotEmpty() && !seen.add(id))
                    problems.add("cashTransactions[$i]: duplicate cash movement ID")
            }
        }
        data.optJSONArray("payments")?.let { payments ->
            for(i in 0 until payments.length()) {
                val row=payments.optJSONObject(i) ?: continue
                val ref=row.optString("referenceId").trim()
                val type=row.optString("referenceType")
                if(ref.isNotEmpty() && type=="sale" && ref !in saleIds)
                    problems.add("payments[$i]: unknown sale reference")
                if(ref.isNotEmpty() && type=="purchase" && ref !in purchaseIds)
                    problems.add("payments[$i]: unknown purchase reference")
            }
        }
        val supplierIds=HashSet<String>()
        data.optJSONArray("suppliers")?.let { suppliers ->
            for(i in 0 until suppliers.length()) {
                val id=suppliers.optJSONObject(i)?.optString("id").orEmpty().trim()
                if(id.isNotEmpty()) supplierIds.add(id)
            }
        }
        data.optJSONArray("supplierLedger")?.let { ledger ->
            for(i in 0 until ledger.length()) {
                val row=ledger.optJSONObject(i) ?: continue
                val supplier=row.optString("supplierId").trim()
                if(supplier.isNotEmpty() && supplier !in supplierIds)
                    problems.add("supplierLedger[$i]: unknown supplier")
            }
        }
        data.optJSONArray("purchases")?.let { purchases ->
            for(i in 0 until purchases.length()) {
                val row=purchases.optJSONObject(i) ?: continue
                val supplier=row.optString("supplierId").trim()
                if(supplier.isNotEmpty() && supplier !in supplierIds)
                    problems.add("purchases[$i]: unknown supplier")
            }
        }
        return problems
    }
}
