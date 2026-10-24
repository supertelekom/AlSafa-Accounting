package com.alsafa.native6

import org.json.JSONObject
import java.math.BigDecimal

/** Read-only movement totals; never interprets them as opening vault balances. */
object LegacyCashAudit {
    fun inspect(payload:String):JSONObject {
        val data=JSONObject(payload).getJSONObject("data")
        val entries=data.optJSONArray("cashTransactions") ?: return JSONObject()
        val totals=sortedMapOf<String,BigDecimal>()
        for(i in 0 until entries.length()) {
            val row=entries.optJSONObject(i) ?: continue
            val currency=row.optString("currency")
            val vault=row.optString("vault")
            val type=row.optString("type")
            require(currency in setOf("USD","TRY","SYP"))
            val amount=row.optString("amount").toBigDecimalOrNull()
                ?: throw IllegalArgumentException("Invalid cash amount")
            val key="$vault|$currency|$type"
            totals[key]=(totals[key] ?: BigDecimal.ZERO)+amount
        }
        val result=JSONObject()
        totals.forEach { (key,value) -> result.put(key,value.toPlainString()) }
        return result
    }
}
