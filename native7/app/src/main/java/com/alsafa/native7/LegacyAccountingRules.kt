package com.alsafa.native7

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/** Read-only source-compatible totals and costs. Does not post money or inventory. */
internal object LegacyAccountingRules {
    fun number(value:Any?):Double {
        if(value==null || value==JSONObject.NULL)return 0.0
        val s=value.toString().trim()
        val prefix=Regex("^[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?").find(s)?.value
        return prefix?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
    }
    private fun items(rows:JSONArray?,field:String):Double {
        var total=0.0
        for(i in 0 until (rows?.length() ?: 0)) {
            val row=rows?.optJSONObject(i) ?: continue
            total+=number(row.opt("qty"))*number(row.opt(field))
        }
        return total
    }
    fun saleTotal(sale:JSONObject)=number(sale.opt("total"))
    fun saleSubtotal(sale:JSONObject):Double {
        val value=sale.opt("subtotal")
        if(value!=null && value!=JSONObject.NULL && value.toString().toDoubleOrNull()!=null)
            return max(0.0,number(value))
        val rows=sale.optJSONArray("items")
        var total=0.0
        for(i in 0 until (rows?.length() ?: 0)) {
            val item=rows?.optJSONObject(i) ?: continue
            total+=max(0.0,number(item.opt("qty")))*max(0.0,number(item.opt("price")))
        }
        return total
    }
    fun saleCost(sale:JSONObject)=items(sale.optJSONArray("items"),"cost")
    fun repairPartsTotal(repair:JSONObject)=items(repair.optJSONArray("usedParts"),"price")
    fun repairPartsCost(repair:JSONObject)=items(repair.optJSONArray("usedParts"),"cost")
    fun repairLaborPrice(repair:JSONObject):Double {
        if(repair.has("laborPrice") && !repair.isNull("laborPrice"))
            return max(0.0,number(repair.opt("laborPrice")))
        return max(0.0,max(0.0,number(repair.opt("price")))-repairPartsTotal(repair))
    }
    fun repairInvoiceTotal(repair:JSONObject):Double {
        if(!repair.has("laborPrice") || repair.isNull("laborPrice"))return number(repair.opt("price"))
        val base=repairLaborPrice(repair)+repairPartsTotal(repair)
        return base+base*max(0.0,number(repair.opt("taxRate")))/100.0
    }
}
