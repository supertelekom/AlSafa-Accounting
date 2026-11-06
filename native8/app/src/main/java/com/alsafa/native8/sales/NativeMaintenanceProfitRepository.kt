package com.alsafa.native8.sales
import com.alsafa.native8.maintenance.MaintenanceDao
import org.json.JSONObject

class NativeMaintenanceProfitRepository(private val dao:MaintenanceDao,private val cash:LegacyPaymentDao) {
 data class Result(val audit:LegacyHistoricalSalesProfitReader.Audit,val ids:Set<String>)
 suspend fun read(until:String?=null):Result {
  val records=dao.nativeFinancialRecords().filter { row ->
   // A newly created repair after the report cutoff must not block an earlier month.
   until==null || row.date<=until || (row.delivered_date.isNotBlank() && row.delivered_date<=until)
  }
  val source=mutableListOf<JSONObject>();val issues=mutableListOf<String>()
  for(row in records) try {
   val raw=JSONObject(row.original_json)
   require(raw.getString("id")==row.id && raw.getString("status")==row.status &&
    raw.getString("currency")==row.currency && raw.optString("received").toBigDecimal().compareTo(row.received.toBigDecimal())==0) { "Room/JSON receipt mismatch" }
   if(row.status=="تم التسليم") require(raw.getString("deliveredDate")==row.delivered_date) { "Room/JSON delivery date mismatch" }
   // Older records did not have the new delivery evidence format. Their saved
   // received/cost/rates remain the source, just as in the original backup.
   if(raw.optInt("financialSnapshotVersion")==0) { source+=raw;continue }
   if(row.status=="تم التسليم") {
    require(raw.optString("price").toBigDecimal().compareTo(row.price.toBigDecimal())==0) { "Room/JSON invoice total mismatch" }
    val lines=raw.getJSONArray("paymentLines")
    val expected=mutableMapOf<String,java.math.BigDecimal>()
    for(i in 0 until lines.length()) {
     val line=lines.getJSONObject(i);val unit=line.getString("currency")
     expected[unit]=(expected[unit] ?: java.math.BigDecimal.ZERO)+line.optString("amount").toBigDecimal()
    }
    val posted=cash.cashboxMovements(row.id).groupBy { it.currency }.mapValues { (_,rows) ->
     rows.fold(java.math.BigDecimal.ZERO) { sum,movement -> sum+movement.amount.toBigDecimal() } }
    require(posted.keys==expected.keys && expected.all { (unit,value) -> posted.getValue(unit).compareTo(value)==0 }) {
     "Delivery cash evidence differs from recorded receipt" }
   }
   for(key in listOf("nativeReturns","nativePayments")) {
    val a=raw.optJSONArray(key) ?: org.json.JSONArray()
    for(i in 0 until a.length()) {
     val e=a.getJSONObject(i)
     require(e.getString("referenceId")==row.id && e.getString("referenceType")=="repair")
     val effect=e.optString(if(key=="nativeReturns") "cashEffect" else "amount").toBigDecimal()
     val movements=cash.cashboxMovements(e.getString("id"))
     require(movements.all { it.currency==e.getString("currency") && it.cashbox==e.getString("vault") }) { "Settlement cash currency/vault mismatch" }
     require(movements.fold(java.math.BigDecimal.ZERO) { total,m -> total+m.amount.toBigDecimal() }.compareTo(effect)==0 &&
      movements.size==(if(effect.signum()==0) 0 else 1)) { "Settlement cash evidence mismatch" }
    }
   }
   source+=raw
  } catch(e:Exception) { issues+="Native repair ${row.id}: ${e.message}" }
  val archiveEvents=mutableListOf<FinancialSourceReconciliation.Event>()
  for(row in dao.allDelivered().filter { it.source_sha256!="native8" && (until==null || it.delivered_date<=until) }) try {
   val raw=JSONObject(row.original_json)
   if(!raw.has("nativeArchiveDelivery")) continue
   val delivery=raw.getJSONObject("nativeArchiveDelivery")
   require(delivery.getString("date")==row.delivered_date)
   val payment=delivery.optJSONObject("payment")
   if(payment!=null) {
    val posted=cash.cashboxMovements(payment.getString("id"))
    require(posted.size==1 && posted.single().currency==payment.getString("paymentCurrency") &&
     posted.single().cashbox==payment.getString("vault") && posted.single().amount.toBigDecimal().compareTo(payment.get("paymentAmount").toString().toBigDecimal())==0) { "Archived delivery cash mismatch" }
   }
   val result=ArchivedRepairDeliveryRules.audit(raw)
   archiveEvents+=result.events;issues+=result.unresolved
  } catch(e:Exception) {issues+="Archived repair ${row.id}: ${e.message}"}
  val audit=NativeMaintenanceProfitReader.extract(source)
  return Result(audit.copy(events=audit.events+archiveEvents,unresolved=audit.unresolved+issues),records.map { it.id }.toSet())
 }
}
