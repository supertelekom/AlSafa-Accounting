package com.alsafa.native8.sales

import com.alsafa.native8.maintenance.MaintenanceRepairEntity
import org.json.JSONObject
import java.math.BigDecimal

/** Read-only cost evidence; does not decrement stock or assume current part cost. */
object MaintenanceCostAudit {
 data class Evidence(val repairId:String,val delivered:Boolean,
  val receiptAmount:BigDecimal?,val partsCostUsd:BigDecimal?,
  val unresolved:List<String>) {
  val complete:Boolean get()=delivered && receiptAmount!=null &&
   partsCostUsd!=null && unresolved.isEmpty()
 }
 fun inspect(repair:MaintenanceRepairEntity):Evidence {
  val issues=mutableListOf<String>()
  val raw=runCatching { JSONObject(repair.original_json) }.getOrElse {
   return Evidence(repair.id,false,null,null,listOf("Invalid original JSON"))
  }
  val delivered=repair.status=="تم التسليم" && repair.delivered_date.isNotBlank()
  if(!delivered) issues+="Delivery not confirmed"
  val received=repair.received.toBigDecimalOrNull()
  if(received==null || received<BigDecimal.ZERO) issues+="Missing receipt"
  val lines=raw.optJSONArray("usedParts")
  var total=BigDecimal.ZERO
  var valid=true
  if(lines!=null) for(i in 0 until lines.length()) {
   val part=lines.optJSONObject(i)
   val qty=part?.optString("qty")?.toBigDecimalOrNull()
   // A current part catalog price is not a historical cost snapshot.
   val cost=part?.optString("costUsd")?.toBigDecimalOrNull()
   if(qty==null || qty<BigDecimal.ZERO || cost==null || cost<BigDecimal.ZERO) {
    valid=false
    break
   }
   total+=qty*cost
  }
  if(lines==null || !valid) issues+="Historical parts cost not verified"
  if(repair.currency!="USD") issues+="Historical repair exchange rate not verified"
  return Evidence(repair.id,delivered,received,
   if(lines!=null && valid && repair.currency=="USD") total else null,issues)
 }
}
