package com.alsafa.native8.sales

/**
 * Historical invoice audit. The archive retains the exact source JSON.
 * Classifying an invoice is NOT enough to calculate profit: historical item
 * costs, taxes, returns, FX and online collection dates must be reconciled.
 */
object LegacySalesProfitAudit {
 data class Summary(
  val storeInvoices:Int,
  val onlineInvoices:Int,
  val unresolvedIds:List<String>,
  val archivedInvoices:Int
 ) {
  val readyForProfit:Boolean get()=archivedInvoices==0 && unresolvedIds.isEmpty()
 }
 fun inspect(invoices:List<LegacySaleEntity>):Summary {
  var store=0
  var online=0
  val unresolved=mutableListOf<String>()
  val seen=mutableSetOf<String>()
  for(invoice in invoices) {
   if(!seen.add(invoice.legacy_id)) {
    unresolved+=invoice.legacy_id
    continue
   }
   try {
    when(LegacySaleChannel.fromOriginalJson(invoice.original_json)) {
     SalesChannelProfit.Channel.STORE -> store++
     SalesChannelProfit.Channel.ONLINE -> online++
    }
   } catch(_:Exception) {
    unresolved+=invoice.legacy_id
   }
  }
  return Summary(store,online,unresolved,invoices.size)
 }
}
