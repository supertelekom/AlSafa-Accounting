package com.alsafa.native8.sales
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class LegacyMaintenanceProfitReaderTest {
 private fun fixture()=JSONObject(requireNotNull(javaClass.getResourceAsStream("/historical-finance-v359.json")).bufferedReader().use { it.readText() })
 private fun rows(f:JSONObject,key:String)=f.getJSONArray(key).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
 private fun close(expected:Any,actual:BigDecimal)=assertTrue("$expected != $actual",(expected.toString().toBigDecimal()-actual).abs()<BigDecimal("0.00000001"))
 @Test fun allOriginalRepairsAndReturnMatchSource() {
  val f=fixture();val a=LegacyMaintenanceProfitReader.extract(rows(f,"repairs"),rows(f,"payments"),rows(f,"returns"))
  assertEquals(emptyList<String>(),a.unresolved)
  val expected=f.getJSONObject("expected")
  assertEquals(298,rows(expected,"events").size)
  val byKey=a.events.associateBy { it.sourceKey }
  assertEquals(299,byKey.size)
  for(e in rows(expected,"events")) {
   val actual=requireNotNull(byKey[e.getString("key")])
   assertEquals(e.getString("date"),actual.recognizedOn.toString())
   close(e.get("revenue"),actual.revenueUsd);close(e.get("cost"),actual.costUsd)
  }
  for(e in rows(expected,"returns")) close(e.get("profit"),requireNotNull(byKey["legacy:repair-return:"+e.getString("id")]).profitUsd)
 }
 @Test fun allExpensesAndWithdrawalsMatchOriginalIdsFxAndPartners() {
  val f=fixture();val expected=f.getJSONObject("expected")
  for((key,isWithdrawal) in listOf("expenses" to false,"withdrawals" to true)) {
   val a=LegacyExpenseWithdrawalReader.extract(rows(f,key),isWithdrawal)
   assertTrue(a.unresolved.isEmpty());assertEquals(if(isWithdrawal)72 else 22,a.entries.size)
   val byId=a.entries.associateBy { it.id }
   for(e in rows(expected,key)) {
    val actual=requireNotNull(byId[e.getString("id")]);close(e.get("usd"),actual.money.usd())
    assertEquals(e.getString("date"),actual.date)
    if(isWithdrawal) assertEquals(e.getString("partnerId"),actual.partnerId)
   }
  }
 }
 @Test fun missingCostAndDuplicatePaymentQuarantineRepair() {
  val f=fixture();val records=rows(f,"repairs");val payments=rows(f,"payments")
  val p=payments.first { it.optString("paymentType")!="repair-exchange-difference" }
  val id=p.getString("referenceId");val r=records.first { it.getString("id")==id }
  r.remove("cost")
  val a=LegacyMaintenanceProfitReader.extract(listOf(r),listOf(p),emptyList())
  assertTrue(a.events.isEmpty());assertFalse(a.unresolved.isEmpty())
  val duplicate=LegacyMaintenanceProfitReader.extract(listOf(r),listOf(p,p),emptyList())
  assertTrue(duplicate.events.isEmpty());assertFalse(duplicate.unresolved.isEmpty())
 }
 @Test fun duplicateExpenseMissingFxAndUnknownPartnerAreNotZero() {
  val f=fixture();val expense=rows(f,"expenses").first();val withdrawal=rows(f,"withdrawals").first()
  assertTrue(LegacyExpenseWithdrawalReader.extract(listOf(expense,expense),false).entries.isEmpty())
  expense.put("currency","TRY").remove("rateSnapshot")
  assertFalse(LegacyExpenseWithdrawalReader.extract(listOf(expense),false).unresolved.isEmpty())
  withdrawal.put("partnerId","unknown")
  assertFalse(LegacyExpenseWithdrawalReader.extract(listOf(withdrawal),true).unresolved.isEmpty())
 }
 @Test fun declaredExternalWithdrawalsAreExcludedFromFamilyBalances() {
  val external=JSONObject("""{"partnerType":"external","partnerId":"external-old","amount":500}""")
  val a=LegacyExpenseWithdrawalReader.extract(listOf(external),true)
  assertTrue(a.entries.isEmpty());assertTrue(a.unresolved.isEmpty())
 }
}
