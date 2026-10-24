package com.alsafa.native6

import org.json.JSONObject

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
            .put("movementCountsByDate",movements)
            .put("closingRowsMissingDate",missingDate)
            .put("daysWithMultipleClosings",multiple)
            .put("movementDaysWithoutClosing",movementDaysWithoutClosing)
            .put("posting","NONE")
            .put("warning","غياب إقفال يومي لا يعني فقدان النقد، وتعدد الإقفالات لا يعني مضاعفة الرصيد")
    }
}
