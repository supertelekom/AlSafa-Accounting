package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only cross-reference audit for AlSafa Backup v2.
 * Does not post financial entries or infer cash balances.
 */
object LegacyFinancialLinkAudit {
    fun inspect(payload:String):JSONObject {
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup" &&
            root.optInt("formatVersion")==2) { "Expected AlSafa Backup v2" }
        val data=root.getJSONObject("data")
        val collections=listOf("sales","repairs","purchases","suppliers",
            "payments","returns","cashTransactions","supplierLedger",
            "partnerWithdrawals","dailyClosings","expenses")
        val ids=HashMap<String,HashSet<String>>()
        val duplicateIds=JSONArray()
        val missingIds=JSONArray()
        for(section in collections) {
            val seen=HashSet<String>()
            val array=data.optJSONArray(section) ?: JSONArray()
            for(index in 0 until array.length()) {
                val row=array.optJSONObject(index) ?: continue
                val id=row.optString("id").trim()
                if(id.isEmpty()) missingIds.put("$section[$index]")
                else if(!seen.add(id)) duplicateIds.put("$section[$index]")
            }
            ids[section]=seen
        }
        // Invoice numbers are display identifiers, not reliable unique source keys.
        // Preserve every source ID even if the old app reused an invoice number.
        val duplicateInvoiceNumbers=JSONArray()
        val invoiceNumbers=HashMap<String,String>()
        for(section in listOf("sales","repairs")) {
            val array=data.optJSONArray(section) ?: JSONArray()
            for(index in 0 until array.length()) {
                val row=array.optJSONObject(index) ?: continue
                val number=row.optString("invoiceNo").trim()
                if(number.isEmpty()) continue
                val previous=invoiceNumbers.putIfAbsent(number,"$section[$index]")
                if(previous!=null && duplicateInvoiceNumbers.length()<100)
                    duplicateInvoiceNumbers.put("$section[$index] conflicts with $previous: $number")
            }
        }
        val targets=mapOf("sale" to "sales","repair" to "repairs",
            "purchase" to "purchases","supplier" to "suppliers",
            "partnerWithdrawal" to "partnerWithdrawals",
            "expense" to "expenses", "dailyClosing" to "dailyClosings")
        val missingReferences=JSONArray()
        val ambiguousReferences=JSONArray()
        val linked=JSONObject()
        for(section in listOf("payments","returns","cashTransactions")) {
            val array=data.optJSONArray(section) ?: JSONArray()
            var linkedCount=0
            var missingCount=0
            var ambiguousCount=0
            for(index in 0 until array.length()) {
                val row=array.optJSONObject(index) ?: continue
                val ref=row.optString("referenceId").trim()
                if(ref.isEmpty()) continue // Transfers/openings may have no reference.
                val referenceType=row.optString("referenceType").trim()
                val target=targets[referenceType]
                if(target==null) {
                    ambiguousCount++
                    if(ambiguousReferences.length()<100)
                        ambiguousReferences.put("$section[$index]: $referenceType")
                    continue
                }
                if(ref in ids.getValue(target)) linkedCount++
                else {
                    missingCount++
                    if(missingReferences.length()<100)
                        missingReferences.put("$section[$index]: $referenceType")
                }
            }
            linked.put(section,JSONObject()
                .put("matched",linkedCount)
                .put("missing",missingCount)
                .put("unknownReferenceType",ambiguousCount))
        }
        // Cash transactions frequently mirror invoices, payments, supplier
        // settlements and withdrawals. Record this relationship as evidence;
        // never add mirrored movements to cumulative invoice paid again.
        val cashByReference=JSONObject()
        val cashRows=data.optJSONArray("cashTransactions") ?: JSONArray()
        val cashGroups=linkedMapOf<String,MutableList<JSONObject>>()
        for(index in 0 until cashRows.length()) {
            val row=cashRows.optJSONObject(index) ?: continue
            val type=row.optString("referenceType").trim()
            val ref=row.optString("referenceId").trim()
            if(type.isNotEmpty() && ref.isNotEmpty()) {
                val key="$type|$ref"
                cashGroups.getOrPut(key){mutableListOf()}.add(row)
            }
        }
        val mirrored=JSONObject()
        for(section in listOf("sales","repairs","purchases","expenses","partnerWithdrawals")) {
            val type=when(section) {
                "sales" -> "sale"
                "repairs" -> "repair"
                "purchases" -> "purchase"
                "expenses" -> "expense"
                else -> "partnerWithdrawal"
            }
            val array=data.optJSONArray(section) ?: JSONArray()
            var withCash=0
            var withoutCash=0
            var cashRowsCount=0
            for(index in 0 until array.length()) {
                val row=array.optJSONObject(index) ?: continue
                val related=cashGroups["$type|"+row.optString("id")].orEmpty()
                if(related.isEmpty()) withoutCash++ else withCash++
                cashRowsCount+=related.size
            }
            mirrored.put(section,JSONObject()
                .put("recordsWithReferencedCash",withCash)
                .put("recordsWithoutReferencedCash",withoutCash)
                .put("referencedCashRows",cashRowsCount))
        }
        cashByReference.put("bySourceSection",mirrored)
            .put("cashRows",cashRows.length())
            .put("groupedReferenceCount",cashGroups.size)
            .put("deduplicationRequired",true)
        val supplierLedger=data.optJSONArray("supplierLedger") ?: JSONArray()
        var missingSuppliers=0
        for(index in 0 until supplierLedger.length()) {
            val supplier=supplierLedger.optJSONObject(index)?.optString("supplierId").orEmpty()
            if(supplier.isNotBlank() && supplier !in ids.getValue("suppliers"))
                missingSuppliers++
        }
        return JSONObject()
            .put("readOnly",true)
            .put("financialPostingStatus","NOT_POSTED")
            .put("collectionCounts",JSONObject().also { result ->
                collections.forEach { result.put(it,data.optJSONArray(it)?.length() ?: 0) }
            })
            .put("duplicateIds",duplicateIds)
            .put("duplicateInvoiceNumbers",duplicateInvoiceNumbers)
            .put("missingIds",missingIds)
            .put("linkSummary",linked)
            .put("cashMirrorEvidence",cashByReference)
            .put("missingReferencesSample",missingReferences)
            .put("ambiguousReferencesSample",ambiguousReferences)
            .put("missingSupplierLedgerReferences",missingSuppliers)
    }
}
