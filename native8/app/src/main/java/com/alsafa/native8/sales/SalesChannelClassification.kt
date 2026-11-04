package com.alsafa.native8.sales

/** Unknown legacy channel must never silently default to in-store. */
object SalesChannelClassification {
 const val STORE="store"
 const val ONLINE="online"
 fun requireVerified(raw:String?):SalesChannelProfit.Channel=when(raw) {
  STORE -> SalesChannelProfit.Channel.STORE
  ONLINE -> SalesChannelProfit.Channel.ONLINE
  else -> error("نوع البيع غير مثبت؛ يمنع اعتماد الربح")
 }
}
