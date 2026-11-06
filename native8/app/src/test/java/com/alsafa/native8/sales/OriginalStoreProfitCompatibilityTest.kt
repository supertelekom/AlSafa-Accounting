package com.alsafa.native8.sales
import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import java.math.BigDecimal
class OriginalStoreProfitCompatibilityTest {
 private fun dao(returned:String="0",cost:String="6"):SalesFinancialAuditDao = Proxy.newProxyInstance(SalesFinancialAuditDao::class.java.classLoader,arrayOf(SalesFinancialAuditDao::class.java)) { _,method,_ ->
  when(method.name) {
   "compatibilityReturns" -> emptyList<RoomSaleReturn>()
   "compatibilityAdjustments" -> emptyList<StoreProfitAdjustment>()
   "unpostedStoreSales" -> listOf(RoomSale("s",426,100,"عميل","","10","2","0","آجل",null))
   "compatibilityLines" -> listOf(RoomSaleLine("l","s","native8","p",null,"ساعة","1","12",cost,"2",returned))
   else -> error("Unexpected write/access: ${method.name}")
  }
 } as SalesFinancialAuditDao
 private fun returnedDao(cancel:Boolean):SalesFinancialAuditDao = Proxy.newProxyInstance(SalesFinancialAuditDao::class.java.classLoader,arrayOf(SalesFinancialAuditDao::class.java)) { _,method,_ ->
  when(method.name) {
   "unpostedStoreSales" -> listOf(RoomSale("s",419,100,"","","20","4","0",if(cancel)"ملغاة" else "مدفوع",null))
   "compatibilityLines" -> listOf(RoomSaleLine("l","s","native8","p",null,"ساعة","2","12","6","4",if(cancel)"2" else "1"))
   "compatibilityAdjustments" -> emptyList<StoreProfitAdjustment>()
   "compatibilityReturns" -> listOf(RoomSaleReturn("r","s","l","1",200,"مرتجع"))+if(cancel)listOf(RoomSaleReturn("c","s","l","1",300,"إلغاء الفاتورة"))else emptyList()
   else -> error("Unexpected write")
  }
 } as SalesFinancialAuditDao
 @Test fun partialReturnUsesItsOwnDateAndSavedDiscount()=runBlocking {
  val a=OriginalStoreProfitCompatibility.read(returnedDao(false),400)
  assertTrue(a.unresolved.toString(),a.unresolved.isEmpty())
  assertEquals("8",a.events.single().profit_usd)
  assertEquals(200L,a.adjustments.single().created_at)
  assertEquals(0,BigDecimal("-4").compareTo(a.adjustments.single().profit_delta_usd.toBigDecimal()))
  assertTrue(OriginalStoreProfitCompatibility.read(returnedDao(false),150).adjustments.isEmpty())
 }
 @Test fun cancellationAfterReturnReversesOnlyRemainingProfitAtCancellationDate()=runBlocking {
  val dao=returnedDao(true);val a=OriginalStoreProfitCompatibility.read(dao,400)
  assertTrue(a.unresolved.toString(),a.unresolved.isEmpty())
  assertEquals(listOf(200L,300L),a.adjustments.map { it.created_at })
  assertEquals(0,(a.events.single().profit_usd.toBigDecimal()+a.adjustments.fold(BigDecimal.ZERO){sum,r->sum+r.profit_delta_usd.toBigDecimal()}).signum())
  assertEquals(1,OriginalStoreProfitCompatibility.read(dao,250).adjustments.size)
  assertEquals(a,OriginalStoreProfitCompatibility.read(dao,400))
 }
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
