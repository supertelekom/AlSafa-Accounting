package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Reconstructs end-of-backup cash per vault and currency from signed legacy cash movements.
 * Never imports movements as new receipts or assumes that daily closing transfers are extra cash.
 */
object LegacyVaultSnapshot {
    private val currencies=setOf("USD","TRY","SYP")
    private val vaultMap=mapOf("drawer" to "drawer","external" to "external",
        "shamcash" to "sham","sham" to "sham","bank" to "bank")
    fun inspect(payload:String):JSONObject {
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup" && root.optInt("formatVersion")>=2)
        val movements=root.getJSONObject("data").optJSONArray("cashTransactions") ?: JSONArray()
        val sums=sortedMapOf<String,BigDecimal>()
        val counts=sortedMapOf<String,Int>()
        val invalid=JSONArray()
        val negative=JSONArray()
        val duplicates=JSONArray()
        val ids=mutableSetOf<String>()
        for(i in 0 until movements.length()) {
            val row=movements.optJSONObject(i)
            if(row==null) { invalid.put(i);continue }
            val id=row.optString("id").trim()
            if(id.isNotBlank() && !ids.add(id)) duplicates.put(i)
            val vault=vaultMap[row.optString("vault")]
            val currency=row.optString("currency")
            val amount=row.optString("amount").toBigDecimalOrNull()
            if(vault==null || currency !in currencies || amount==null ||
                row.optString("type").isBlank() || row.optString("date").isBlank()) {
                invalid.put(i)
                continue
            }
            val key="$vault|$currency"
            sums[key]=(sums[key] ?: BigDecimal.ZERO)+amount
            counts[key]=(counts[key] ?: 0)+1
        }
        val balances=JSONObject()
        for((key,value) in sums) {
            balances.put(key,value.toPlainString())
            if(value<BigDecimal.ZERO) negative.put(key)
        }
        return JSONObject().put("movementCount",movements.length())
            .put("balances",balances).put("movementCounts",JSONObject(counts as Map<*,*>))
            .put("invalidMovementIndexes",invalid).put("duplicateMovementIndexes",duplicates)
            .put("negativeFinalBalances",negative).put("posting","NONE")
            .put("readyToActivate",invalid.length()==0 && duplicates.length()==0 &&
                negative.length()==0)
            .put("warning","These are historical signed cash sums, not verified physical vault counts")
    }
}
