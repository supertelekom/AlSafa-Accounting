package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/** Validate legacy payment links without creating receipts or modifying invoice balances. */
object LegacyPaymentLinkAudit {
    fun inspect(payload:String):JSONObject {
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup" && root.optInt("formatVersion")>=2)
        val data=root.getJSONObject("data")
        val ids=linkedMapOf<String,Set<String>>()
        for(section in listOf("sales","repairs","purchases")) {
            val rows=data.optJSONArray(section) ?: JSONArray()
            val found=mutableSetOf<String>()
            for(i in 0 until rows.length()) {
                val id=rows.optJSONObject(i)?.optString("id") ?: ""
                if(id.isNotBlank()) found.add(id)
            }
            ids[section]=found
        }
        val missing=JSONArray()
        val unresolved=JSONArray()
        val invalidAmounts=JSONArray()
        val duplicatePaymentIds=JSONArray()
        val seenPaymentIds=mutableSetOf<String>()
        val evidence=linkedMapOf<String,BigDecimal>()
        val crossCurrencyPayments=JSONArray()
        val invalidPaymentCurrencyRows=JSONArray()
        val invalidReceivedAmountRows=JSONArray()
        val payments=data.optJSONArray("payments") ?: JSONArray()
        for(i in 0 until payments.length()) {
            val payment=payments.optJSONObject(i) ?: continue
            val paymentId=payment.optString("id").trim()
            if(paymentId.isNotBlank() && !seenPaymentIds.add(paymentId)) duplicatePaymentIds.put(i)
            val type=payment.optString("referenceType").trim().lowercase()
            val section=when(type) {
                "sale","sales" -> "sales"
                "repair","repairs" -> "repairs"
                "purchase","purchases" -> "purchases"
                else -> null
            }
            val id=payment.optString("referenceId").trim()
            if(id.isBlank()) missing.put(i)
            else if(section==null || id !in (ids[section] ?: emptySet())) unresolved.put(i)
            val invoiceCurrency=payment.optString("currency").trim()
            val receivedCurrency=payment.optString("paymentCurrency",invoiceCurrency).trim()
            if(invoiceCurrency !in setOf("USD","TRY","SYP") || receivedCurrency !in setOf("USD","TRY","SYP"))
                invalidPaymentCurrencyRows.put(i)
            else if(invoiceCurrency!=receivedCurrency) crossCurrencyPayments.put(i)
            val received=payment.optString("paymentAmount",payment.optString("amount")).toBigDecimalOrNull()
            if(received==null || received<BigDecimal.ZERO) invalidReceivedAmountRows.put(i)
            val amount=payment.optString("amount").toBigDecimalOrNull()
            if(amount==null || amount<BigDecimal.ZERO) invalidAmounts.put(i)
            else if(section!=null && id in (ids[section] ?: emptySet())) {
                val key="$section:$id"
                evidence[key]=(evidence[key] ?: BigDecimal.ZERO)+amount
            }
        }
        val totals=JSONObject()
        for((key,value) in evidence) totals.put(key,value.toPlainString())
        return JSONObject().put("paymentCount",payments.length())
            .put("missingReferenceRows",missing)
            .put("unresolvedReferenceRows",unresolved)
            .put("invalidAmountRows",invalidAmounts)
            .put("duplicatePaymentIdRows",duplicatePaymentIds)
            .put("crossCurrencyPaymentRows",crossCurrencyPayments)
            .put("invalidPaymentCurrencyRows",invalidPaymentCurrencyRows)
            .put("invalidReceivedAmountRows",invalidReceivedAmountRows)
            .put("linkedPaymentEvidence",totals)
            .put("posting","NONE")
    }
}
