package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/** Read-only payment to cash receipt comparison. Never posts to the ledger. */
object LegacyPaymentCashAudit {
    fun inspect(payload:String):JSONObject {
        val root=JSONObject(payload)
        return inspectRoot(root)
    }

    fun inspectRoot(root:JSONObject):JSONObject {
        require(root.optString("format")=="alsafa-accounting-backup" && root.optInt("formatVersion")>=2)
        val data=root.getJSONObject("data")
        val cash=data.optJSONArray("cashTransactions") ?: JSONArray()
        val payments=data.optJSONArray("payments") ?: JSONArray()
        val matches=JSONArray()
        val unmatched=JSONArray()
        val ambiguous=JSONArray()
        val used=mutableSetOf<Int>()
        for(i in 0 until payments.length()) {
            val p=payments.optJSONObject(i) ?: continue
            val received=p.optString("paymentAmount",p.optString("amount")).toBigDecimalOrNull()
            val candidates=mutableListOf<Int>()
            for(j in 0 until cash.length()) {
                if(j in used) continue
                val c=cash.optJSONObject(j) ?: continue
                val value=c.optString("amount").toBigDecimalOrNull()
                if(received!=null && value!=null && received.compareTo(value)==0 &&
                    c.optString("type")=="income" &&
                    c.optString("referenceType")==p.optString("referenceType") &&
                    c.optString("referenceId")==p.optString("referenceId") &&
                    c.optString("date")==p.optString("date") &&
                    c.optString("currency")==p.optString("paymentCurrency",p.optString("currency")))
                    candidates.add(j)
            }
            if(candidates.size==1) {
                used.add(candidates[0])
                matches.put(JSONObject().put("paymentIndex",i).put("cashIndex",candidates[0]))
            } else if(candidates.isEmpty()) unmatched.put(i)
            else ambiguous.put(JSONObject().put("paymentIndex",i).put("candidates",JSONArray(candidates)))
        }
        return JSONObject().put("paymentCount",payments.length()).put("matches",matches)
            .put("unmatched",unmatched).put("ambiguous",ambiguous).put("posting","NONE")
    }
}
