package com.alsafa.native8.sales

/** Read-only channel census: no invoice rewriting and no historical cash reposting. */
class LegacySaleChannelAuditRepository(private val db:AppDatabase) {
 data class Census(val online:Int,val store:Int,val unrecognized:List<String>) {
  val verified:Int get()=online+store
  val complete:Boolean get()=unrecognized.isEmpty()
 }
 suspend fun auditSource(sourceSha256:String):Census {
  require(sourceSha256.isNotBlank())
  var online=0
  var store=0
  val errors=mutableListOf<String>()
  for(sale in db.legacySaleDao().legacyBySource(sourceSha256)) {
   try {
    when(LegacySaleChannel.fromOriginalJson(sale.original_json)) {
     SalesChannelProfit.Channel.ONLINE -> online++
     SalesChannelProfit.Channel.STORE -> store++
    }
   } catch(e:Exception) {
    errors+=sale.legacy_id
   }
  }
  return Census(online,store,errors)
 }
}
