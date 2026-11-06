package com.alsafa.native8.maintenance
import org.json.JSONObject

object QuickRepairButtonRules {
 fun validate(value:JSONObject):JSONObject {
  val row=JSONObject(value.toString())
  require(row.getString("id").isNotBlank())
  require(row.optString("label").trim().isNotEmpty()){ "اسم الزر مطلوب" }
  val mode=if(row.getString("id")=="manual-problem")"manual" else row.optString("priceMode")
  require(mode in setOf("fixed","manual")){ "اختر طريقة السعر" }
  val price=QuickRepairRules.amount(row.optString("price","0"))
  require(mode=="manual" || price.signum()>0){ "أدخل سعراً ثابتاً أكبر من صفر" }
  val qty=QuickRepairRules.amount(row.optString("qty","1"))
  require(qty.signum()>0 && qty.stripTrailingZeros().scale()<=0){ "الكمية عدد صحيح موجب" }
  val order=row.optString("order","0").toIntOrNull()
  require(order!=null && order>=0){ "الترتيب عدد صحيح غير سالب" }
  return row.put("label",row.getString("label").trim()).put("priceMode",mode).put("price",if(mode=="manual")"0" else price.toPlainString()).put("qty",qty.intValueExact()).put("order",order)
 }
}
