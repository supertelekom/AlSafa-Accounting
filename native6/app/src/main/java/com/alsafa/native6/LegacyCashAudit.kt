package com.alsafa.native6

import org.json.JSONObject
import java.math.BigDecimal

/** Read-only movement totals; never interprets them as opening vault balances. */
object LegacyCashAudit {
    fun inspect(payload:String):JSONObject {
        val data=JSONObject(payload).getJSONObject("data")
        val entries=data.optJSONArray("cashTransactions") ?: return JSONObject()
        val totals=sortedMapOf<String,BigDecimal>()
        val counts=sortedMapOf<String,Int>()
        val invalid=org.json.JSONArray()
        val missingVaults=org.json.JSONArray()
        for(i in 0 until entries.length()) {
            val row=entries.optJSONObject(i) ?: continue
            val currency=row.optString("currency")
            val vault=row.optString("vault")
            val type=row.optString("type")
            if(vault.isBlank()) missingVaults.put(i)
            val amount=row.optString("amount").toBigDecimalOrNull()
            if(currency !in setOf("USD","TRY","SYP") || amount==null) {
                invalid.put(i)
                continue
            }
            val key="$vault|$currency|$type"
            totals[key]=(totals[key] ?: BigDecimal.ZERO)+amount
            counts[key]=(counts[key] ?: 0)+1
        }
        val result=JSONObject()
        totals.forEach { (key,value) -> result.put(key,value.toPlainString()) }
        return JSONObject().put("totals",result)
            .put("counts",JSONObject(counts as Map<*,*>))
            .put("invalidRows",invalid)
            .put("missingVaultRows",missingVaults)
            .put("posting","NONE")
    }
}
