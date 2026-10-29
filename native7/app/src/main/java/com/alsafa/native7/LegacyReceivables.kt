package com.alsafa.native7

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/**
 * Read-only port of the original main.js paidFor/refundedFor/effectiveTotalFor/
 * netPaidFor/balanceFor/paymentStatusFor rules. No postings or stock writes.
 */
internal object LegacyReceivables {
    private fun number(value:Any?):Double = when(value) {
        is Number -> value.toDouble().takeIf { it.isFinite() } ?: 0.0
        is String -> value.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
    private fun rows(array:JSONArray?)= (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
    private fun matching(array:JSONArray?,type:String,id:String)=rows(array).filter {
        it.optString("referenceType")==type && it.optString("referenceId")==id
    }
    data class Result(val paid:Double,val refunded:Double,val netPaid:Double,
        val effectiveTotal:Double,val balance:Double,val status:String)

    /**
     * originalTotal must be supplied by the separately ported saleTotal or
     * repairInvoiceTotal rule. Never silently assume record.total for repairs.
     */
    fun calculate(type:String,record:JSONObject,originalTotal:Double,
        payments:JSONArray?,returns:JSONArray?):Result {
        require(type=="sale" || type=="repair")
        val id=record.getString("id")
        val matchedPayments=matching(payments,type,id)
        val matchedReturns=matching(returns,type,id)
        val base=number(record.opt(if(type=="sale") "paid" else "received"))
        val paid=base+matchedPayments.sumOf { number(it.opt("amount")) }
        val refunded=matchedReturns.sumOf { max(0.0,number(it.opt("refundAmount"))) }
        val netPaid=max(0.0,paid-refunded)
        val effective=max(0.0,originalTotal-matchedReturns.sumOf { number(it.opt("creditAmount")) })
        val collectionDiscount=if(type=="sale" && record.optString("saleType","store")=="online")
            matchedPayments.sumOf { max(0.0,number(it.opt("onlineCollectionDiscount"))) } else 0.0
        val balance=max(0.0,effective-netPaid-collectionDiscount)
        val status=when {
            effective<=0.0001 && matchedReturns.isNotEmpty() -> "مسوّاة/مسترجعة"
            effective<=0.0001 && type=="repair" && paid<=0.0001 -> "عند الاستلام"
            effective<=0.0001 -> record.optString("paymentStatus","—")
            balance<0.01 && (paid>0.0001 || collectionDiscount>0) -> "مدفوع بالكامل"
            paid>0.0001 -> "دفعة جزئية"
            type=="repair" -> "عند الاستلام"
            else -> record.optString("paymentStatus","آجل")
        }
        return Result(paid,refunded,netPaid,effective,balance,status)
    }
}
