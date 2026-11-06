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
  val audit=NativeMaintenanceProfitReader.extract(source)
  return Result(audit.copy(unresolved=audit.unresolved+issues),records.map { it.id }.toSet())
 }
}
