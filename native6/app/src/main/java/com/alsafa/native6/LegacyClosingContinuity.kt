package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Historical cash continuity diagnostics. No balance or ledger writes. */
object LegacyClosingContinuity {
    fun inspect(payload:String):JSONObject {
        val root=JSONObject(payload)
        return inspectRoot(root)
    }

    fun inspectRoot(root:JSONObject):JSONObject {
        require(root.optString("format")=="alsafa-accounting-backup")
        require(root.optInt("formatVersion")>=2)
        val closings=root.getJSONObject("data").optJSONArray("dailyClosings") ?: JSONArray()
        val rows=mutableListOf<Pair<LocalDate,Int>>()
        val invalidDates=JSONArray()
        val missingClosingRows=JSONArray()
        for(i in 0 until closings.length()) {
            if(closings.optJSONObject(i)==null) missingClosingRows.put(i)
            val raw=closings.optJSONObject(i)?.optString("date") ?: ""
            val date=runCatching { LocalDate.parse(raw) }.getOrNull()
            if(date==null) invalidDates.put(i) else rows.add(date to i)
        }
        rows.sortWith(compareBy<Pair<LocalDate,Int>> { it.first }.thenBy { it.second })
        val transitions=JSONArray()
        val invalidAmounts=JSONArray()
        val carryMismatches=JSONArray()
        val duplicateClosingDates=JSONArray()
        for(i in 1 until rows.size) {
            if(rows[i].first==rows[i-1].first) duplicateClosingDates.put(rows[i].first.toString())
        }
        val currencies=listOf("USD","TRY","SYP")
        fun number(row:JSONObject,field:String,currency:String,index:Int):BigDecimal? {
            val values=row.optJSONObject(field) ?: return null
            if(!values.has(currency)) return null
            val amount=values.optString(currency).toBigDecimalOrNull()
            if(amount==null) invalidAmounts.put("$index:$field:$currency")
            return amount
        }
        for((date,index) in rows) {
            val row=closings.getJSONObject(index)
            for(currency in currencies) {
                val actual=number(row,"drawerActual",currency,index)
                val transfer=number(row,"drawerTransfer",currency,index)
                val carry=number(row,"drawerNext",currency,index)
                if(actual!=null && transfer!=null && carry!=null && (actual-transfer-carry).abs()>BigDecimal("0.000001"))
                    carryMismatches.put(JSONObject().put("date",date.toString()).put("currency",currency)
                        .put("sourceIndex",index).put("difference",(actual-transfer-carry).toPlainString()))
            }
        }
        for(i in 0 until rows.size-1) {
            val (fromDate,fromIndex)=rows[i]
            val (toDate,toIndex)=rows[i+1]
            if(fromDate==toDate) continue
            val previous=closings.getJSONObject(fromIndex)
            val later=closings.getJSONObject(toIndex)
            val carried=JSONObject()
            val missing=JSONArray()
            for(currency in currencies) {
                val value=number(previous,"drawerNext",currency,fromIndex)
                if(value==null) missing.put(currency)
                else carried.put(currency,value.toPlainString())
            }
            transitions.put(JSONObject()
                .put("fromDate",fromDate.toString()).put("toDate",toDate.toString())
                .put("daysBetween",ChronoUnit.DAYS.between(fromDate,toDate))
                .put("fromIndex",fromIndex).put("toIndex",toIndex)
                .put("previousCarry",carried).put("missingCarryCurrencies",missing)
                .put("status","OPENING_BALANCE_NOT_VERIFIED")
                .put("nextClosingHasActual",later.optJSONObject("drawerActual")!=null))
        }
        return JSONObject().put("closingCount",closings.length())
            .put("transitions",transitions).put("invalidDateRows",invalidDates)
            .put("missingClosingRows",missingClosingRows)
            .put("invalidAmountFields",invalidAmounts)
            .put("arithmeticMismatches",carryMismatches)
            .put("duplicateClosingDates",duplicateClosingDates)
            .put("posting","NONE")
            .put("note","Previous closing carry is not proof of next-day opening balance")
    }
}
