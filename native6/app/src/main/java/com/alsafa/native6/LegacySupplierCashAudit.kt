package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Reconciles supplier opening-debt settlements against existing cash outflows.
 * This is evidence only: never posts cash, liabilities or purchase payments.
 */
object LegacySupplierCashAudit {
    fun inspect(payload:String):JSONObject {
        val root=JSONObject(payload)
        return inspectRoot(root)
    }

    fun inspectRoot(root:JSONObject):JSONObject {
        require(root.optString("format")=="alsafa-accounting-backup" && root.optInt("formatVersion")>=2)
        val data=root.getJSONObject("data")
        val ledger=data.optJSONArray("supplierLedger") ?: JSONArray()
        val cash=data.optJSONArray("cashTransactions") ?: JSONArray()
        val matched=JSONArray()
        val missing=JSONArray()
        val ambiguous=JSONArray()
        val invalid=JSONArray()
        val used=mutableSetOf<Int>()
        var settlements=0
        for(i in 0 until ledger.length()) {
            val row=ledger.optJSONObject(i) ?: continue
            if(row.optString("kind")!="payment") continue
            settlements++
            val amount=row.optString("amount").toBigDecimalOrNull()
            val supplier=row.optString("supplierId").trim()
            val currency=row.optString("currency")
            if(amount==null || amount<=BigDecimal.ZERO || supplier.isBlank() ||
                currency !in setOf("USD","TRY","SYP")) {
                invalid.put(i)
                continue
            }
            val candidates=mutableListOf<Int>()
            for(j in 0 until cash.length()) {
                if(j in used) continue
                val movement=cash.optJSONObject(j) ?: continue
                val cashAmount=movement.optString("amount").toBigDecimalOrNull() ?: continue
                if(movement.optString("type")=="supplier-opening-debt-payment" &&
                    movement.optString("referenceType")=="supplier" &&
                    movement.optString("referenceId")==supplier &&
                    movement.optString("currency")==currency &&
                    movement.optString("date")==row.optString("date") &&
                    (row.optString("vault").isBlank() ||
                        row.optString("vault")==movement.optString("vault")) &&
                    cashAmount.compareTo(amount.negate())==0) candidates.add(j)
            }
            when(candidates.size) {
                0 -> missing.put(i)
                1 -> {
                    used.add(candidates[0])
                    matched.put(JSONObject().put("ledgerIndex",i).put("cashIndex",candidates[0])
                        .put("supplierId",supplier).put("currency",currency)
                        .put("amount",amount.toPlainString()))
                }
                else -> ambiguous.put(JSONObject().put("ledgerIndex",i)
                    .put("cashIndexes",JSONArray(candidates)))
            }
        }
        return JSONObject().put("settlementCount",settlements)
            .put("matched",matched).put("unmatchedLedgerIndexes",missing)
            .put("ambiguousCandidates",ambiguous).put("invalidLedgerIndexes",invalid)
            .put("posting","NONE")
            .put("warning","Matched cash outflows already exist; do not subtract them from cash again")
    }
}
