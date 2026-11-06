package com.alsafa.native8.sales

import org.json.JSONObject
import java.math.BigDecimal

/** Original balanceFor / appendRepairDeliveryPayment, without replaying historical receipts. */
object ArchivedRepairDeliveryRules {
 private val zero=BigDecimal.ZERO
 fun due(raw:JSONObject,payments:List<JSONObject>,returns:List<JSONObject>):BigDecimal {
  val id=raw.getString("id")
  fun linked(rows:List<JSONObject>)=rows.filter { it.optString("referenceType")=="repair" && it.optString("referenceId")==id }
  fun sum(rows:List<JSONObject>,key:String)=rows.fold(zero){a,r->a+r.optString(key,"0").toBigDecimal()}
  val credits=linked(returns)
  val total=(LegacyMaintenanceProfitReader.total(raw)-sum(credits,"creditAmount")).max(zero)
  val paid=(raw.get("received").toString().toBigDecimal()+sum(linked(payments),"amount")-
   credits.fold(zero){a,r->a+r.optString("refundAmount","0").toBigDecimal().max(zero)}).max(zero)
  return (total-paid).max(zero)
 }
 fun ready(status:String)=status=="جاهز للاستلام" || status=="بانتظار التسليم" || status.contains("انتظار التسليم")
 fun audit(raw:JSONObject):LegacyHistoricalSalesProfitReader.Audit {
  val delivery=raw.getJSONObject("nativeArchiveDelivery")
  val original=JSONObject(delivery.getString("original"))
  // Only the new receipt enters this projection. Historical initial/payments stay in staging.
  original.put("received","0")
  val payment=delivery.optJSONObject("payment")
  if(payment==null) return LegacyHistoricalSalesProfitReader.Audit(emptyList(),emptyList())
  val result=LegacyMaintenanceProfitReader.extract(listOf(original),listOf(payment),emptyList())
  return result.copy(events=result.events.map { it.copy(sourceKey="native:repair:${raw.getString("id")}:delivery") })
 }
}
