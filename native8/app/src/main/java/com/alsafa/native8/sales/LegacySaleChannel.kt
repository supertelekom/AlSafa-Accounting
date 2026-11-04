package com.alsafa.native8.sales

import org.json.JSONObject

/**
 * Verified against legacy assets/main.js:
 * sale form line 7933 stores saleType as 'online' or 'store';
 * onlineSaleReceiptEvents (line 2763) checks (sale.saleType||'store')==='online'.
 * The historical archive keeps the original JSON unchanged.
 */
object LegacySaleChannel {
 fun fromOriginalJson(raw:String):SalesChannelProfit.Channel {
  val sale=JSONObject(raw)
  val type=if(sale.isNull("saleType")) "" else sale.optString("saleType","")
  return when(type) {
   "online" -> SalesChannelProfit.Channel.ONLINE
   "store","" -> SalesChannelProfit.Channel.STORE
   else -> error("نوع بيع تاريخي غير معروف: $type")
  }
 }
 fun isOnline(raw:String):Boolean=fromOriginalJson(raw)==SalesChannelProfit.Channel.ONLINE
}
