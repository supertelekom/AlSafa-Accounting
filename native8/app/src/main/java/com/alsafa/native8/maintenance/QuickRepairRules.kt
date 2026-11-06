package com.alsafa.native8.maintenance

import org.json.JSONObject
import org.json.JSONArray
import java.math.BigDecimal
import java.math.RoundingMode

/** main.js quickRepairButtons/saveFatherRepair, preserving the original configuration. */
object QuickRepairRules {
 fun buttons(settings:JSONObject):List<JSONObject> {
  val saved=settings.optJSONArray("quickRepairButtons") ?: JSONArray()
  val rows=(0 until saved.length()).map { JSONObject(saved.getJSONObject(it).toString()) }.toMutableList()
  if(rows.isEmpty()) {
   val names=listOf("بطارية 50 ليرة","بطارية 70 ليرة","بطارية 100 ليرة","بطارية 150 ليرة","حزام بلاستك 100 ليرة","حزام جلد 250 ليرة","قفل ساعة جلد 30 ليرة","قفل ساعة قلاب 70 ليرة")
   val prices=listOf(50,70,100,150,100,250,30,70)
   names.forEachIndexed { i,name -> rows+=JSONObject().put("id","legacy-$i").put("label",name.replace(Regex(" \\d+ ليرة$"),"")).put("price",prices[i]).put("priceMode","fixed").put("partId","").put("qty",1).put("icon",if(i<4) "🔋" else if(i<6) "⌚" else "🔗").put("order",i).put("legacyService",name) }
  }
  if(rows.none { it.optString("id")=="manual-problem" }) rows+=JSONObject("""{"id":"manual-problem","label":"صيانة مشكلة","price":0,"priceMode":"manual","partId":"","qty":1,"icon":"🛠️","order":999}""")
  require(rows.map { it.getString("id") }.distinct().size==rows.size)
  return rows.sortedBy { it.optDouble("order",0.0) }
 }
 fun amount(value:String):BigDecimal = value.trim().map { c -> when(c) { in '٠'..'٩' -> '0'+(c-'٠');in '۰'..'۹' -> '0'+(c-'۰');'٫' -> '.';else -> c } }.joinToString("").toBigDecimal()
 fun financial(settings:JSONObject,button:JSONObject,part:JSONObject?,price:BigDecimal,currency:String,rate:(String,String)->BigDecimal,allowZero:Boolean=false):JSONObject {
  require((price>BigDecimal.ZERO || (allowZero && price.signum()==0)) && currency in setOf("TRY","USD","SYP")) { "أدخل المبلغ النهائي والعملة" }
  val used=JSONArray();var cost=BigDecimal.ZERO
  if(button.optString("partId").isNotBlank()) {
   require(part!=null && part.getString("id")==button.getString("partId")) { "قطعة الصيانة المرتبطة بالزر غير موجودة" }
   val qty=BigDecimal(button.optInt("qty",1).coerceAtLeast(1));require(amount(part.get("stock").toString())>=qty) { "المخزون غير كافٍ" }
   val unit=part.getString("currency");val sourceCost=amount(part.get("cost").toString());require(sourceCost>=BigDecimal.ZERO)
   val fx=rate(unit,currency);val unitCost=sourceCost*fx;cost=unitCost*qty
   used.put(JSONObject().put("partId",part.getString("id")).put("name",part.getString("name")).put("qty",qty.toPlainString())
    .put("price",price.divide(qty,16,RoundingMode.HALF_UP).toPlainString()).put("cost",unitCost.toPlainString()).put("currency",currency)
    .put("costSnapshotVerified",true).put("sourceUnitCost",sourceCost.toPlainString()).put("sourceCostCurrency",unit).put("costExchangeRate",fx.toPlainString()))
  } else {
   val key=button.optString("legacyService")
   val value=settings.optJSONObject("quickRepairCosts")?.optString(key,"0") ?: "0"
   cost=amount(value).max(BigDecimal.ZERO)*rate(settings.optString("quickRepairCostsCurrency","TRY"),"USD")*rate("USD",currency)
  }
  val linked=used.length()>0
  return JSONObject().put("usedParts",used).put("laborPrice",if(linked) "0" else price.toPlainString())
   .put("partsTotal",if(linked) price.toPlainString() else "0").put("partsCost",if(linked) cost.toPlainString() else "0")
   .put("manualCost",if(linked) "0" else cost.toPlainString()).put("cost",cost.toPlainString()).put("price",price.toPlainString())
   .put("currency",currency).put("tax",0).put("taxRate",0).put("partsStockApplied",false)
 }
}
