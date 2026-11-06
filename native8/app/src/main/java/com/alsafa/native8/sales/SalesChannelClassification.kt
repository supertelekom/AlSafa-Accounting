package com.alsafa.native8.sales

/** New writes require an explicit channel. Older saved invoices use OriginalStoreProfitCompatibility. */
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
