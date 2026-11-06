package com.alsafa.native8.maintenance
import org.json.JSONObject
import java.math.BigDecimal

/** Labour-only intake can be delivered explicitly without a hidden readiness step. */
object MaintenanceDeliveryEligibility {
 fun allowed(status:String,raw:JSONObject):Boolean = status=="جاهز للاستلام" ||
  (status=="بانتظار التسليم" && (raw.optJSONArray("usedParts")?.length() ?: 0)==0 &&
   (raw.optString("laborPrice",raw.optString("price","0")).toBigDecimalOrNull() ?: BigDecimal.ZERO)>BigDecimal.ZERO)
}
