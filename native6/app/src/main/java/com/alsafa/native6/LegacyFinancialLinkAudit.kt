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
            "partnerWithdrawals","dailyClosings")
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
        val targets=mapOf("sale" to "sales","repair" to "repairs",
            "purchase" to "purchases","supplier" to "suppliers",
            "partnerWithdrawal" to "partnerWithdrawals")
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
            .put("missingIds",missingIds)
            .put("linkSummary",linked)
            .put("missingReferencesSample",missingReferences)
            .put("ambiguousReferencesSample",ambiguousReferences)
            .put("missingSupplierLedgerReferences",missingSuppliers)
    }
}
