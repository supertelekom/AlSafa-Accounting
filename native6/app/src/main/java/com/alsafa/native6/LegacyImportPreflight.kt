package com.alsafa.native6

import org.json.JSONObject
import java.time.LocalDate

/** Read-only validation; never mutates the active store. */
object LegacyImportPreflight {
    fun inspect(payload:String):List<String> = inspectInventory(payload) + inspectFinancialHistory(payload)

    /** Only imported inventory, suppliers and repairs can block the snapshot transaction. */
    fun inspectInventory(payload:String):List<String> {
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup")
        val data=root.getJSONObject("data")
        require(root.optInt("formatVersion")==2){"Unsupported legacy backup version"}
        val problems=mutableListOf<String>()
        val inventoryIds=HashSet<String>()
        val supplierIds=HashSet<String>()
        val repairIds=HashSet<String>()
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
                val namespace=when(section) {
                    "products", "maintenanceParts" -> inventoryIds
                    "suppliers" -> supplierIds
                    else -> repairIds
                }
                if(id.isEmpty() || !namespace.add(id)) problems.add("$section[$i]: missing or duplicate ID")
                if(section=="products" || section=="maintenanceParts") {
                    val barcode=item.optString("barcode").trim()
                    if(barcode.isNotEmpty() && !barcodes.add(barcode))
                        problems.add("$section[$i]: duplicate barcode")
                    if(item.optString("currency") !in setOf("USD","TRY","SYP"))
                        problems.add("$section[$i]: unsupported currency")
                    val qty=item.optString("stock").toLongOrNull()
                    if(qty==null || qty<0) problems.add("$section[$i]: invalid stock")
                    if(qty!=null && qty>100000000L) problems.add("$section[$i]: stock exceeds supported range")
                    if(item.optString("name").isBlank())
                        problems.add("$section[$i]: missing product name")
                    for(field in listOf("cost","salePrice")) {
                        val amount=item.optString(field,"0").toBigDecimalOrNull()
                        if(amount==null || amount.signum()<0 || amount.stripTrailingZeros().scale()>2)
                            problems.add("$section[$i]: invalid or over-precise $field")
                    }
                }
                if(section=="suppliers" && item.optString("name").isBlank())
                    problems.add("$section[$i]: missing name")
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
        return problems
    }

    /** Archived history is advisory until its accounting semantics are reconciled. */
    fun inspectFinancialHistory(payload:String):List<String> {
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup" && root.optInt("formatVersion")==2) { "Unsupported legacy backup version" }
        val data=root.getJSONObject("data")
        val problems=mutableListOf<String>()
        val validCurrencies=setOf("USD","TRY","SYP")
        for(section in listOf("sales","purchases","expenses","partnerWithdrawals","payments")) {
            val records=data.optJSONArray(section) ?: continue
            val ids=HashSet<String>()
            for(i in 0 until records.length()) {
                val row=records.optJSONObject(i)
                if(row==null) { problems.add("$section[$i]: invalid record"); continue }
                if(section=="sales" || section=="purchases" || section=="payments") {
                    val id=row.optString("id").trim()
                    if(id.isEmpty() || !ids.add(id)) problems.add("$section[$i]: missing or duplicate ID")
                }
                val currency=row.optString("currency").trim()
                if(currency.isNotEmpty() && currency !in validCurrencies)
                    problems.add("$section[$i]: unsupported currency")
                if(section=="sales" || section=="purchases") {
                    val total=row.optString("total").toBigDecimalOrNull()
                    val paid=row.optString("paid").toBigDecimalOrNull()
                    if(total==null || paid==null || paid.signum()<0 || paid>total || total.signum()<0 || total.stripTrailingZeros().scale()>2 || paid.stripTrailingZeros().scale()>2)
                        problems.add("$section[$i]: paid exceeds invoice total")
                }
                val field=if(section=="sales" || section=="purchases") "total" else "amount"
                if(row.has(field) && !row.isNull(field)) {
                    val amount=row.optString(field).toBigDecimalOrNull()
                    if(amount==null || amount.stripTrailingZeros().scale()>2 || (section in setOf("sales","purchases","payments","partnerWithdrawals") && amount.signum()<0)) problems.add("$section[$i]: invalid $field")
                }
            }
        }
        for(section in listOf("sales","purchases","expenses","partnerWithdrawals")) {
            val records=data.optJSONArray(section) ?: continue
            for(i in 0 until records.length()) {
                val row=records.optJSONObject(i) ?: continue
                val date=row.optString("date").trim()
                if(date.isNotEmpty() && runCatching { LocalDate.parse(date).isAfter(LocalDate.now()) }.getOrDefault(true))
                    problems.add("$section[$i]: invalid or future date")
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
        val repairIds=HashSet<String>()
        data.optJSONArray("repairs")?.let { repairs ->
            for(i in 0 until repairs.length()) {
                val id=repairs.optJSONObject(i)?.optString("id").orEmpty().trim()
                if(id.isNotEmpty()) repairIds.add(id)
            }
        }
        data.optJSONArray("returns")?.let { returns ->
            for(i in 0 until returns.length()) {
                val row=returns.optJSONObject(i) ?: continue
                val ref=row.optString("referenceId").trim()
                val type=row.optString("referenceType")
                if(ref.isNotEmpty() && type=="sale" && ref !in saleIds)
                    problems.add("returns[$i]: unknown sale reference")
                if(ref.isNotEmpty() && type=="purchase" && ref !in purchaseIds)
                    problems.add("returns[$i]: unknown purchase reference")
                if(ref.isNotEmpty() && type=="repair" && ref !in repairIds)
                    problems.add("returns[$i]: unknown repair reference")
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
                if(ref.isNotEmpty() && type=="repair" && ref !in repairIds)
                    problems.add("payments[$i]: unknown repair reference")
            }
        }
        data.optJSONArray("supplierLedger")?.let { ledger ->
            for(i in 0 until ledger.length()) {
                val row=ledger.optJSONObject(i) ?: continue
                val amount=row.optString("amount").toBigDecimalOrNull()
                if(amount==null || amount.signum()<0)
                    problems.add("supplierLedger[$i]: invalid amount")
                val currency=row.optString("currency")
                if(currency.isNotBlank() && currency !in validCurrencies)
                    problems.add("supplierLedger[$i]: unsupported currency")
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
