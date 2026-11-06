package com.alsafa.native8.sales
import com.alsafa.native8.maintenance.QuickRepairRules
import com.alsafa.native8.maintenance.MaintenanceDeliveryEligibility
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.math.BigDecimal
import java.math.MathContext
class QuickRepairRulesTest {
 private fun rate(from:String,to:String):BigDecimal {
  if(from=="TRY" && to=="SYP") return BigDecimal(3)
  if(from=="SYP" && to=="TRY") return BigDecimal.ONE.divide(BigDecimal(3),MathContext.DECIMAL128)
  val rates=mapOf("USD" to BigDecimal.ONE,"TRY" to BigDecimal(50),"SYP" to BigDecimal(140))
  return rates.getValue(to).divide(rates.getValue(from),MathContext.DECIMAL128)
 }
 @Test fun allRestoredButtonsMatchOriginalSaveFatherRepairInThreeCurrencies() {
  val f=JSONObject(javaClass.getResourceAsStream("/quick-repair-v359.json")!!.bufferedReader().use { it.readText() })
  val settings=f.getJSONObject("settings");val buttons=QuickRepairRules.buttons(settings)
  assertEquals(60,buttons.size);assertEquals("manual-problem",buttons.last().getString("id"))
  val parts=f.getJSONArray("parts");val cases=f.getJSONArray("cases");assertEquals(180,cases.length())
  for(i in 0 until cases.length()) {
   val c=cases.getJSONObject(i);val b=buttons.single { it.getString("id")==c.getString("id") }
   val part=(0 until parts.length()).map { parts.getJSONObject(it) }.firstOrNull { it.getString("id")==b.optString("partId") }
   val actual=QuickRepairRules.financial(settings,b,part,c.get("price").toString().toBigDecimal(),c.getString("currency"),::rate)
   for(key in listOf("price","laborPrice","partsTotal","partsCost","manualCost","cost"))
    assertTrue("${b.getString("label")} $key",(actual.get(key).toString().toBigDecimal()-c.get(key).toString().toBigDecimal()).abs()<BigDecimal("0.00000001"))
  }
 }
 @Test fun originalFallbackAndArabicAmounts() {
  val rows=QuickRepairRules.buttons(JSONObject());assertEquals(9,rows.size)
  assertEquals("صيانة مشكلة",rows.last().getString("label"));assertEquals(BigDecimal("250.5"),QuickRepairRules.amount("٢٥٠٫٥"))
 }
 @Test fun pendingSimplifiedPartRepairCanBeDeliveredButNotTwice() {
  val raw=JSONObject("""{"price":70,"laborPrice":0,"isSimplifiedQuick":true,"usedParts":[{"qty":1}]}""")
  assertTrue(MaintenanceDeliveryEligibility.allowed("بانتظار التسليم",raw));assertFalse(MaintenanceDeliveryEligibility.allowed("تم التسليم",raw))
 }
 @Test fun missingLinkedPartCannotBecomeLabourOnly() {
  try { QuickRepairRules.financial(JSONObject(),JSONObject("""{"partId":"missing"}"""),null,BigDecimal.TEN,"USD",::rate);fail() } catch(_:IllegalArgumentException) {}
 }
}
