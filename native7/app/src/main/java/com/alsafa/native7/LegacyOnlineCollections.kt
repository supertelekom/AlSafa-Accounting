package com.alsafa.native7

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/** Read-only port of original onlineSaleReceiptEvents and onlineSaleReceiptCost.
 * Currency conversion is injected so a caller cannot silently invent rates.
 */
internal object LegacyOnlineCollections {
    data class Receipt(val saleId:String,val date:String,val time:String,
        val amount:Double,val discount:Double,val source:String,
        val paymentId:String?,val costRecognized:Double)
    fun events(sales:JSONArray,payments:JSONArray,cashTransactions:JSONArray,
        convert:(Double,String,String,JSONObject?)->Double?):List<Receipt> {
        val result=ArrayList<Receipt>()
        for(i in 0 until sales.length()) {
            val sale=sales.optJSONObject(i) ?: continue
            if(sale.optString("saleType","store")!="online")continue
            val id=sale.optString("id")
            val date=sale.optString("date")
            val saleCurrency=sale.optString("currency")
            val relevant=ArrayList<JSONObject>()
            for(j in 0 until payments.length()) {
                val p=payments.optJSONObject(j) ?: continue
                if(p.optString("referenceType")=="sale" && p.optString("referenceId")==id &&
                    LegacyAccountingRules.number(p.opt("amount"))>0 &&
                    !p.optBoolean("returnAdjustment") && !p.optBoolean("nonCash")) relevant.add(p)
            }
            val sameDay=relevant.filter { it.optString("date")==date }
                .sumOf { max(0.0,LegacyAccountingRules.number(it.opt("amount"))) }
            var openingCash=0.0
            var hasCashRows=false
            for(j in 0 until cashTransactions.length()) {
                val row=cashTransactions.optJSONObject(j) ?: continue
                if(row.optString("type")!="income" || row.optString("referenceType")!="sale" ||
                    row.optString("referenceId")!=id || row.optString("date")!=date ||
                    LegacyAccountingRules.number(row.opt("amount"))<=0)continue
                hasCashRows=true
                val rate=row.optJSONObject("rateSnapshot") ?: sale.optJSONObject("rateSnapshot")
                val converted=convert(LegacyAccountingRules.number(row.opt("amount")),
                    row.optString("currency"),saleCurrency,rate)
                if(converted!=null) openingCash+=max(0.0,converted)
            }
            val corrected=sale.has("paymentMetadataCorrectedAt") ||
                sale.has("paymentCurrencyCorrectedAt") || sale.has("financialFootprintRepairedAt")
            val explicitToRemove=if(corrected)relevant.sumOf {
                max(0.0,LegacyAccountingRules.number(it.opt("amount"))) } else sameDay
            val opening=if(hasCashRows) max(0.0,openingCash-sameDay)
                else max(0.0,LegacyAccountingRules.number(sale.opt("paid"))-explicitToRemove)
            val initial=min(max(0.0,LegacyAccountingRules.saleTotal(sale)),opening)
            val total=max(0.0,LegacyAccountingRules.saleTotal(sale))
            val cost=LegacyAccountingRules.saleCost(sale)
            fun recognized(amount:Double,discount:Double):Double =
                if(total<=0)0.0 else cost*min(1.0,max(0.0,(amount+discount)/total))
            if(initial>0)result.add(Receipt(id,date,sale.optString("time"),
                initial,0.0,"initial",null,recognized(initial,0.0)))
            for(p in relevant) {
                val amount=LegacyAccountingRules.number(p.opt("amount"))
                val discount=max(0.0,LegacyAccountingRules.number(p.opt("onlineCollectionDiscount")))
                result.add(Receipt(id,p.optString("date"),p.optString("time"),amount,discount,
                    "payment",p.optString("id"),recognized(amount,discount)))
            }
        }
        return result
    }
}
