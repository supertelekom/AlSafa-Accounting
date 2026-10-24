package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject

/** Verify original return-to-invoice links before any historical debt adjustments. */
object LegacyReturnLinkAudit {
    fun inspect(payload:String):JSONObject {
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup" && root.optInt("formatVersion")>=2)
        val data=root.getJSONObject("data")
        val sections=listOf("sales","repairs","purchases")
        val ids=linkedMapOf<String,Set<String>>()
        for(section in sections) {
            val values=mutableSetOf<String>()
            val rows=data.optJSONArray(section) ?: JSONArray()
            for(i in 0 until rows.length()) {
                val id=rows.optJSONObject(i)?.optString("id") ?: ""
                if(id.isNotBlank()) values.add(id)
            }
            ids[section]=values
        }
        val missing=JSONArray()
        val unresolved=JSONArray()
        val linked=JSONObject()
        val duplicateReturnIds=JSONArray()
        val seenReturnIds=mutableSetOf<String>()
        val returns=data.optJSONArray("returns") ?: JSONArray()
        for(i in 0 until returns.length()) {
            val row=returns.optJSONObject(i) ?: continue
            val returnId=row.optString("id").trim()
            if(returnId.isNotBlank() && !seenReturnIds.add(returnId)) duplicateReturnIds.put(i)
            val id=row.optString("referenceId").trim()
            val section=when(row.optString("referenceType").trim().lowercase()) {
                "sale","sales" -> "sales"
                "repair","repairs" -> "repairs"
                "purchase","purchases" -> "purchases"
                else -> null
            }
            if(id.isBlank()) missing.put(i)
            else if(section==null || id !in (ids[section] ?: emptySet())) unresolved.put(i)
            else {
                val key="$section:$id"
                val indexes=linked.optJSONArray(key) ?: JSONArray()
                indexes.put(i)
                linked.put(key,indexes)
            }
        }
        return JSONObject().put("returnCount",returns.length())
            .put("missingReferenceRows",missing)
            .put("unresolvedReferenceRows",unresolved)
            .put("duplicateReturnIdRows",duplicateReturnIds)
            .put("linkedReturnIndexes",linked)
            .put("posting","NONE")
    }
}
