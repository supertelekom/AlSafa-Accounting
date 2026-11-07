package com.alsafa.native8.sales

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alsafa.native8.data.operational.LocalProductEntity
import com.alsafa.native8.data.operational.RoomProductStockRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.math.BigDecimal

/** Device test: real Room/SQLite rollback, isolated database, no operational database writes. */
class SaleExchangeTransactionTest {
 private fun n(s:String)=s.toBigDecimal()
 private fun eq(expected:String,actual:String)=assertEquals(0,n(expected).compareTo(n(actual)))
 private suspend fun seed(db:AppDatabase){for(id in listOf("a","b"))db.operationalInventoryWriteDao().insertLocal(LocalProductEntity(id,"""{"name":"$id","stock":"5","currency":"USD","salePrice":"100","cost":"60"}""",1,1))}
 private suspend fun invoice(c:Context,e:RoomSalesEngine,channel:String):String {
  val d=SaleDraft("عميل","123",listOf(SaleLine("local","a",null,"A",n("1"),n("100"),n("60"))),listOf(SalePayment("drawer","USD",n("100"),"USD",n("1"),n("100"))),channel=channel)
  return e.save(d,SalesEngine(c).calculate(d))
 }
 private suspend fun request(db:AppDatabase,id:String)=RoomSalesEngine.ExchangeRequest("test-exchange",id,db.salesPostingDao().lines(id).single().id,n("1"),SaleLine("local","b",null,"B",n("1"),n("80"),n("50")),"external","USD",n("1"),n("0"),"استبدال")
 @Test fun refundAndCancellationDoNotDuplicateCash()=runBlocking {
  val c=ApplicationProvider.getApplicationContext<Context>()
  for(channel in listOf("store","online")){
   val db=Room.inMemoryDatabaseBuilder(c,AppDatabase::class.java).build()
   try{seed(db);val e=RoomSalesEngine(c,db);val id=invoice(c,e,channel);val q=request(db,id);val p=e.previewExchange(q)
    e.exchange(q,p.fingerprint);e.exchange(q,p.fingerprint)
    eq("80",db.salesPostingDao().sale(id)!!.paid)
    val moves=db.legacyPaymentDao().cashboxMovements(id);assertEquals(2,moves.size);eq("-20",moves.single{it.cashbox=="external"}.amount)
    assertEquals(1,db.salesFinancialAuditDao().compatibilityReturns(id).size)
    val stock=RoomProductStockRepository(db);eq("5",stock.currentStock("local","a"));eq("4",stock.currentStock("local","b"))
    e.cancelWithRefund(id);eq("0",db.legacyPaymentDao().cashboxMovements(id).fold(BigDecimal.ZERO){a,m->a+n(m.amount)}.toPlainString());eq("5",stock.currentStock("local","b"))
    if(channel=="online")eq("0",(db.salesFinancialAuditDao().profits(id).fold(BigDecimal.ZERO){a,p->a+n(p.recognized_profit)}-db.salesFinancialAuditDao().reversals(id).fold(BigDecimal.ZERO){a,p->a+n(p.reversed_profit)}).toPlainString())
   }finally{db.close()}
  }
 }
 @Test fun failedReplacementRollsBackRefundStockAndDocument()=runBlocking {
  val c=ApplicationProvider.getApplicationContext<Context>();val db=Room.inMemoryDatabaseBuilder(c,AppDatabase::class.java).build()
  try{seed(db);val e=RoomSalesEngine(c,db);val id=invoice(c,e,"store");val q=request(db,id);val p=e.previewExchange(q)
   db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_exchange_stock BEFORE INSERT ON product_stock_movements WHEN NEW.note LIKE 'بديل استبدال%' BEGIN SELECT RAISE(ABORT,'injected failure'); END")
   assertTrue(runCatching{e.exchange(q,p.fingerprint)}.isFailure)
   eq("100",db.salesPostingDao().sale(id)!!.paid);eq("100",db.salesPostingDao().sale(id)!!.total)
   assertEquals(1,db.legacyPaymentDao().cashboxMovements(id).size)
   assertTrue(db.salesFinancialAuditDao().compatibilityReturns(id).isEmpty())
   assertTrue(db.storeSaleProfitDao().adjustments(id).isEmpty())
   assertFalse(db.salesPostingDao().sale(id)!!.metadata_json.contains("nativeExchanges"))
   val stock=RoomProductStockRepository(db);eq("4",stock.currentStock("local","a"));eq("5",stock.currentStock("local","b"))
  }finally{db.close()}
 }
}
