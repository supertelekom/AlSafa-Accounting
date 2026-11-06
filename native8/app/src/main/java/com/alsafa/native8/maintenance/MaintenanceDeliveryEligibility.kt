package com.alsafa.native8.maintenance
import org.json.JSONObject

/** Both legacy ready labels describe the same explicit readiness state. */
object MaintenanceDeliveryEligibility {
 fun allowed(status:String,@Suppress("UNUSED_PARAMETER") raw:JSONObject):Boolean =
  status=="جاهز للاستلام" || status=="بانتظار التسليم"
 fun display(status:String)=if(status=="جاهز للاستلام" || status=="بانتظار التسليم") "جاهزة للتسليم" else status
}
