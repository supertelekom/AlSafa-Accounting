package com.alsafa.native7

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Deterministic, per-document read-only accounting fingerprints.
 * A reference runner of the old JS can compare each ID, not only aggregates.
 * Currency values are NEVER summed across different currencies.
 */
internal object LegacyInvoiceParity {
    data class Invoice(
        val type:String, val id:String, val invoiceNo:String, val currency:String,
        val date:String, val total:Double, val cost:Double, val paid:Double,
        val refunded:Double, val effectiveTotal:Double, val netPaid:Double,
        val balance:Double, val status:String
    )
    data class Difference(val id:String,val field:String,val expected:String,val actual:String)
    fun snapshot(data:JSONObject):List<Invoice> {
        val payments=data.optJSONArray("payments")
        val returns=data.optJSONArray("returns")
        val result=ArrayList<Invoice>()
        for((section,type) in listOf("sales" to "sale","repairs" to "repair")) {
            val rows=data.optJSONArray(section) ?: continue
            for(i in 0 until rows.length()) {
                val row=rows.optJSONObject(i) ?: continue
                val rec=LegacyReceivables.calculate(type,row,payments,returns)
                result.add(Invoice(type,row.optString("id"),row.optString("invoiceNo"),
                    row.optString("currency"),row.optString("date"),
                    if(type=="sale")LegacyAccountingRules.saleTotal(row)
                        else LegacyAccountingRules.repairInvoiceTotal(row),
                    if(type=="sale")LegacyAccountingRules.saleCost(row)
                        else LegacyAccountingRules.repairPartsCost(row),
                    rec.paid,rec.refunded,rec.effectiveTotal,rec.netPaid,
                    rec.balance,rec.status))
            }
        }
        return result
    }
    fun compare(expected:List<Invoice>,actual:List<Invoice>,
        tolerance:Double=0.000001):List<Difference> {
        require(tolerance>=0 && tolerance.isFinite())
        val result=ArrayList<Difference>()
        val actualById=actual.groupBy { it.type to it.id }
        val expectedById=expected.groupBy { it.type to it.id }
        for((key,rows) in expectedById) {
            val id="${key.first}:${key.second}"
            if(rows.size!=1) {
                result.add(Difference(id,"duplicate_expected","1",rows.size.toString()))
                continue
            }
            val other=actualById[key]
            if(other==null || other.size!=1) {
                result.add(Difference(id,"record","1",(other?.size ?: 0).toString()))
                continue
            }
            val e=rows.single()
            val a=other.single()
            fun str(field:String,x:String,y:String) {
                if(x!=y)result.add(Difference(id,field,x,y))
            }
            fun num(field:String,x:Double,y:Double) {
                if(!x.isFinite() || !y.isFinite() || abs(x-y)>tolerance)
                    result.add(Difference(id,field,x.toString(),y.toString()))
            }
            str("invoiceNo",e.invoiceNo,a.invoiceNo)
            str("currency",e.currency,a.currency)
            str("date",e.date,a.date)
            str("status",e.status,a.status)
            num("total",e.total,a.total)
            num("cost",e.cost,a.cost)
            num("paid",e.paid,a.paid)
            num("refunded",e.refunded,a.refunded)
            num("effectiveTotal",e.effectiveTotal,a.effectiveTotal)
            num("netPaid",e.netPaid,a.netPaid)
            num("balance",e.balance,a.balance)
        }
        for((key,rows) in actualById)if(key !in expectedById)
            result.add(Difference("${key.first}:${key.second}","unexpected_record","0",rows.size.toString()))
        return result
    }
    fun json(snapshot:List<Invoice>):JSONArray {
        val out=JSONArray()
        for(x in snapshot)out.put(JSONObject().apply {
            put("type",x.type);put("id",x.id);put("invoiceNo",x.invoiceNo)
            put("currency",x.currency);put("date",x.date)
            put("total",x.total);put("cost",x.cost);put("paid",x.paid)
            put("refunded",x.refunded);put("effectiveTotal",x.effectiveTotal)
            put("netPaid",x.netPaid);put("balance",x.balance);put("status",x.status)
        })
        return out
    }
}
