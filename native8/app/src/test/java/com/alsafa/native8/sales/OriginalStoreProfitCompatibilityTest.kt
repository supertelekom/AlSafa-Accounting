package com.alsafa.native8.sales
import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import java.math.BigDecimal
class OriginalStoreProfitCompatibilityTest {
 private fun dao(returned:String="0",cost:String="6"):SalesFinancialAuditDao = Proxy.newProxyInstance(SalesFinancialAuditDao::class.java.classLoader,arrayOf(SalesFinancialAuditDao::class.java)) { _,method,_ ->
  when(method.name) {
   "unpostedStoreSales" -> listOf(RoomSale("s",426,100,"عميل","","10","2","0","آجل",null))
   "compatibilityLines" -> listOf(RoomSaleLine("l","s","native8","p",null,"ساعة","1","12",cost,"2",returned))
   else -> error("Unexpected write/access: ${method.name}")
  }
 } as SalesFinancialAuditDao
 @Test fun absentTypeRecognizesStoreOnInvoiceDateEvenUnpaid()=runBlocking {
  val dao=dao();val a=OriginalStoreProfitCompatibility.read(dao,200)
  assertTrue(a.unresolved.isEmpty());val e=a.events.single()
  assertEquals(100L,e.recognized_at);assertEquals(0,BigDecimal("4").compareTo(e.profit_usd.toBigDecimal()))
  assertEquals(a,OriginalStoreProfitCompatibility.read(dao,200))
 }
 @Test fun missingCostDoesNotBecomeZero()=runBlocking {
  val a=OriginalStoreProfitCompatibility.read(dao(cost=""),200)
  assertTrue(a.events.isEmpty());assertTrue(a.unresolved.single().contains("426"))
 }
 @Test fun returnedInvoiceRequiresDatedReturnInsteadOfChangingOriginalProfit()=runBlocking {
  assertFalse(OriginalStoreProfitCompatibility.read(dao(returned="1"),200).unresolved.isEmpty())
 }
}
