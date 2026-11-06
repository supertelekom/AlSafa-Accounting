package com.alsafa.native8.sales
import com.alsafa.native8.maintenance.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class QuickRepairButtonRulesTest {
 private fun row()=JSONObject("""{"id":"custom","label":" بطارية ","priceMode":"fixed","price":"١٠٠","qty":"2","order":"3","partId":"part-7"}""")
 @Test fun linkedButtonRetainsQuantityAndPriceAcrossSettingsReload() {
  val saved=QuickRepairButtonRules.validate(row())
  val restored=QuickRepairRules.buttons(JSONObject().put("quickRepairButtons",org.json.JSONArray().put(saved))).first{it.getString("id")=="custom"}
  assertEquals("بطارية",restored.getString("label"));assertEquals("100",restored.getString("price"));assertEquals(2,restored.getInt("qty"));assertEquals("part-7",restored.getString("partId"))
 }
 @Test fun manualProblemRemainsUnpriced() {
  val saved=QuickRepairButtonRules.validate(row().put("id","manual-problem"))
  assertEquals("manual",saved.getString("priceMode"));assertEquals("0",saved.getString("price"))
 }
 @Test fun invalidStockQuantitiesAndFixedPricesAreRejected() {
  listOf("0","-1","1.5").forEach{q->assertThrows(IllegalArgumentException::class.java){QuickRepairButtonRules.validate(row().put("qty",q))}}
  assertThrows(IllegalArgumentException::class.java){QuickRepairButtonRules.validate(row().put("price","0"))}
 }
 @Test fun manualZeroPriceIsAllowedButBlankNameIsNot() {
  assertEquals("0",QuickRepairButtonRules.validate(row().put("priceMode","manual").put("price","0")).getString("price"))
  assertThrows(IllegalArgumentException::class.java){QuickRepairButtonRules.validate(row().put("label"," "))}
 }
}
