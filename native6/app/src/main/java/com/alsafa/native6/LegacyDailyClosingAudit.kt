package com.alsafa.native6

import org.json.JSONObject
import java.math.BigDecimal

/**
 * Read-only alignment of daily closing records and cash movement dates.
 * This is a coverage check, not a reconstruction of vault balances.
 */
object LegacyDailyClosingAudit {
    fun inspect(payload:String):JSONObject {
        val data=JSONObject(payload).getJSONObject("data")
        val closings=data.optJSONArray("dailyClosings") ?: org.json.JSONArray()
        val movements=LegacyCashAudit.inspect(payload)
            .getJSONObject("movementCountsByDate")
        val closeCounts=linkedMapOf<String,Int>()
        val missingDate=org.json.JSONArray()
        for(i in 0 until closings.length()) {
            val row=closings.optJSONObject(i) ?: continue
            val date=row.optString("date")
            if(date.isBlank()) {
                missingDate.put(i)
                continue
            }
            closeCounts[date]=(closeCounts[date] ?: 0)+1
        }
        val closingBalances=org.json.JSONArray()
        val invalidBalances=org.json.JSONArray()
        for(i in 0 until closings.length()) {
            val row=closings.optJSONObject(i) ?: continue
            val snapshot=JSONObject().put("date",row.optString("date"))
            for(field in listOf("drawerActual","drawerTransfer","drawerNext","drawerDifference","shamActual","shamDifference")) {
                val source=row.optJSONObject(field) ?: continue
                val amounts=JSONObject()
                for(currency in listOf("USD","TRY","SYP")) {
                    if(!source.has(currency)) continue
                    val value=source.optString(currency).toBigDecimalOrNull()
                    if(value==null) invalidBalances.put("$i:$field:$currency")
                    else amounts.put(currency,value.toPlainString())
                }
                snapshot.put(field,amounts)
            }
            closingBalances.put(snapshot)
        }
        val carryMismatches=org.json.JSONArray()
        val incompleteCarry=org.json.JSONArray()
        for(i in 0 until closings.length()) {
            val row=closings.optJSONObject(i) ?: continue
            val actual=row.optJSONObject("drawerActual")
            val transfer=row.optJSONObject("drawerTransfer")
            val next=row.optJSONObject("drawerNext")
            if(actual==null || transfer==null || next==null) {
                incompleteCarry.put(i)
                continue
            }
            for(currency in listOf("USD","TRY","SYP")) {
                val a=actual.optString(currency,"0").toBigDecimalOrNull()
                val t=transfer.optString(currency,"0").toBigDecimalOrNull()
                val n=next.optString(currency,"0").toBigDecimalOrNull()
                if(a!=null && t!=null && n!=null && a-t!=n)
                    carryMismatches.put("$i:$currency")
            }
        }
        val orderedDates=closeCounts.keys.sorted()
        val datedCarry=org.json.JSONArray()
        for(date in orderedDates) {
            for(i in 0 until closings.length()) {
                val closing=closings.optJSONObject(i) ?: continue
                if(closing.optString("date")!=date) continue
                datedCarry.put(JSONObject().put("date",date).put("index",i)
                    .put("drawerNext",closing.optJSONObject("drawerNext") ?: JSONObject()))
            }
        }
        val carryGaps=org.json.JSONArray()
        for(i in 0 until datedCarry.length()-1) {
            val earlier=datedCarry.getJSONObject(i)
            val later=datedCarry.getJSONObject(i+1)
            if(earlier.getString("date")==later.getString("date")) continue
            val next=earlier.getJSONObject("drawerNext")
            val comparison=JSONObject()
                .put("fromDate",earlier.getString("date"))
                .put("toDate",later.getString("date"))
                .put("previousCarry",next)
                .put("status","AWAITING_NEXT_DAY_OPENING_BALANCE")
            carryGaps.put(comparison)
        }
        val multiple=org.json.JSONArray()
        for((date,count) in closeCounts) if(count>1) multiple.put(date)
        val movementDaysWithoutClosing=org.json.JSONArray()
        val dates=movements.keys()
        while(dates.hasNext()) {
            val date=dates.next()
            if(date !in closeCounts) movementDaysWithoutClosing.put(date)
        }
        return JSONObject()
            .put("closingCountsByDate",JSONObject(closeCounts as Map<*,*>))
            .put("orderedClosingDates",org.json.JSONArray(orderedDates))
            .put("datedCarryForward",datedCarry)
            .put("carryForwardTransitions",carryGaps)
            .put("movementCountsByDate",movements)
            .put("closingRowsMissingDate",missingDate)
            .put("closingBalanceSnapshots",closingBalances)
            .put("invalidClosingBalanceFields",invalidBalances)
            .put("drawerCarryForwardMismatches",carryMismatches)
            .put("incompleteCarryForwardRows",incompleteCarry)
            .put("daysWithMultipleClosings",multiple)
            .put("movementDaysWithoutClosing",movementDaysWithoutClosing)
            .put("posting","NONE")
            .put("warning","غياب إقفال يومي لا يعني فقدان النقد، وتعدد الإقفالات لا يعني مضاعفة الرصيد")
    }
}
