package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Historical partner withdrawals are attributed ONLY to the transaction month.
 * Never apply October withdrawals against September unpaid profit.
 * This is an audit report; profit allocation requires separate reconciliation.
 */
object LegacyPartnerWithdrawalReport {
    fun inspect(payload:String,month:String):JSONObject {
        require(Regex("\\d{4}-(0[1-9]|1[0-2])").matches(month)) { "الشهر يجب أن يكون YYYY-MM" }
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup" &&
            root.optInt("formatVersion")==2)
        val records=root.getJSONObject("data").optJSONArray("partnerWithdrawals") ?: JSONArray()
        val grouped=sortedMapOf<String,BigDecimal>()
        val entries=JSONArray()
        var otherMonthRows=0
        for(i in 0 until records.length()) {
            val row=records.getJSONObject(i)
            val date=row.optString("date")
            require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) {
                "تاريخ سحب شريك غير صالح في السجل $i"
            }
            if(!date.startsWith("$month-")) { otherMonthRows++;continue }
            val currency=row.optString("currency")
            require(currency in setOf("USD","TRY","SYP")) { "عملة سحب شريك غير مدعومة" }
            val amount=row.optString("amount","0").toBigDecimalOrNull()
                ?: throw IllegalArgumentException("مبلغ سحب غير صالح في السجل $i")
            require(amount>=BigDecimal.ZERO) { "مبلغ سحب سالب في السجل $i" }
            val partner=row.optString("partnerId").ifBlank {
                row.optString("partnerName").ifBlank { row.optString("partner").ifBlank { row.optString("person","غير محدد") } }
            }
            val key="$partner|$currency"
            grouped[key]=(grouped[key] ?: BigDecimal.ZERO)+amount
            entries.put(JSONObject().put("date",date).put("partner",partner)
                .put("currency",currency).put("amount",amount.toPlainString())
                .put("sourceIndex",i))
        }
        val totals=JSONObject()
        for((key,value) in grouped) totals.put(key,value.toPlainString())
        return JSONObject().put("month",month).put("withdrawals",entries)
            .put("totalsByPartnerCurrency",totals)
            .put("otherMonthsExcluded",otherMonthRows)
            .put("historicalProfitIncluded",false)
            .put("posting","NONE")
            .put("warning","سحوبات الشهر المختار فقط؛ لا تُخصم من ربح شهر سابق تلقائياً")
    }
}
