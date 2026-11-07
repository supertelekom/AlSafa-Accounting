package com.alsafa.native8.sales

import org.json.JSONObject
import java.math.BigDecimal

/** Source equivalents of effectiveTotalFor, netPaidFor, balanceFor and returnedQtyFor. */
data class ArchivedSaleBalance(
 val effective:BigDecimal, val paid:BigDecimal, val due:BigDecimal,
 val returnedByLine:Map<Int,BigDecimal>
) {
 companion object {
  fun calculate(sale:JSONObject,payments:List<JSONObject>,returns:List<JSONObject>,newPaid:BigDecimal):ArchivedSaleBalance {
   val zero=BigDecimal.ZERO
   val saleId=sale.getString("id")
   fun linked(record:JSONObject)=record.optString("referenceType")=="sale" && record.optString("referenceId")==saleId
   fun value(record:JSONObject,key:String)=if(record.isNull(key))zero else record.get(key).toString().toBigDecimal()
   val receipts=payments.filter(::linked)
   val moves=returns.filter(::linked)
   val effective=(value(sale,"total")-moves.sumOf{value(it,"creditAmount")}).max(zero)
   val paid=(value(sale,"paid")+receipts.sumOf{value(it,"amount")}+newPaid-moves.sumOf{value(it,"refundAmount").max(zero)}).max(zero)
   val discount=if(sale.optString("saleType")=="online")receipts.sumOf{value(it,"onlineCollectionDiscount").max(zero)} else zero
   val returned=moves.groupBy{it.getInt("itemIndex")}.mapValues{(_,rows)->rows.sumOf{value(it,"qty")}}
   return ArchivedSaleBalance(effective,paid,(effective-paid-discount).max(zero),returned)
  }
 }
}
