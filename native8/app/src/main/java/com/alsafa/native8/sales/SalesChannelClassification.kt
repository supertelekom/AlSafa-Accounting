package com.alsafa.native8.sales

/** Unknown legacy channel must never silently default to in-store. */
object SalesChannelClassification {
 const val STORE="store"
 const val ONLINE="online"
 fun recognizeAtCollection(channel:String?):Boolean = channel==ONLINE
 fun hasUnverified(count:Int):Boolean {
  require(count>=0)
  return count>0
 }
 fun requireVerified(raw:String?):SalesChannelProfit.Channel=when(raw) {
  STORE -> SalesChannelProfit.Channel.STORE
  ONLINE -> SalesChannelProfit.Channel.ONLINE
  else -> error("نوع البيع غير مثبت؛ يمنع اعتماد الربح")
 }
}
