package com.alsafa.native8.sales
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
fun n(s:String)=s.toBigDecimal()
fun eq(actual:String,expected:String){check(n(actual).compareTo(n(expected))==0){"Expected $expected, got $actual"}}
fun profit(d:AppDatabase)=if(d.sales.values.first().channel=="store")d.store.sumOf{n(it.profit_usd)}+d.adjustments.sumOf{n(it.profit_delta_usd)} else d.profits.sumOf{n(it.recognized_profit)}-d.reversals.sumOf{n(it.reversed_profit)}
fun main()=runBlocking {
 var assertions=0
 suspend fun scenario(channel:String,originalPaid:String,replacement:String,cost:String,collected:String,refund:String,due:String,expectedProfit:String){
  val d=AppDatabase();AppDatabase.instance=d;val engine=RoomSalesEngine(android.content.Context())
  d.stock["local/a/null"]="10";d.stock["local/b/null"]="10"
  val pays=if(n(originalPaid).signum()>0)listOf(SalePayment("drawer","USD",n(originalPaid),"USD",n("1"),n(originalPaid))) else emptyList()
  val draft=SaleDraft("عميل","123",listOf(SaleLine("local","a",null,"A",n("1"),n("100"),n("60"))),pays,channel=channel)
  val id=engine.save(draft,SalesEngine(android.content.Context()).calculate(draft));val line=d.lines.values.first()
  val q=RoomSalesEngine.ExchangeRequest("x",id,line.id,n("1"),SaleLine("local","b",null,"B",n("1"),n(replacement),n(cost)),"external","TRY",n("0.02"),n(collected)*n("50"),"تبديل")
  val preview=engine.previewExchange(q);eq(preview.settlement.refund.toPlainString(),refund)
  engine.exchange(q,preview.fingerprint)
  eq(d.debts.getValue(id).balance,due);eq(d.stock.getValue("local/a/null"),"10");eq(d.stock.getValue("local/b/null"),"9");eq(profit(d).toPlainString(),expectedProfit)
  val cash=d.cash.filter{it.cashbox=="external"}.sumOf{n(it.amount)};eq(cash.toPlainString(),((n(collected)-n(refund))*n("50")).toPlainString())
  val snapshot=d.snapshot();engine.exchange(q,preview.fingerprint);check(d.snapshot()==snapshot)
  engine.cancelWithRefund(id);eq(profit(d).toPlainString(),"0");eq(d.stock.getValue("local/b/null"),"10");eq(d.debts.getValue(id).balance,"0")
  val netBase=d.cash.sumOf{n(it.amount)*(if(it.currency=="TRY")n("0.02") else n("1"))};eq(netBase.toPlainString(),"0")
  assertions+=11
 }
 for(channel in listOf("store","online")){
  scenario(channel,"100","120","70","20","0","0","50")
  scenario(channel,"100","80","50","0","20","0","30")
  scenario(channel,"100","100","70","0","0","0","30")
  scenario(channel,"40","120","70","0","0","80",if(channel=="store")"50" else "16.67")
  scenario(channel,"0","80","50","0","0","80",if(channel=="store")"30" else "0")
 }
 // Failure after returning the old item must roll back all preceding mutations.
 val d=AppDatabase();AppDatabase.instance=d;val engine=RoomSalesEngine(android.content.Context());d.stock["local/a/null"]="1";d.stock["local/b/null"]="1"
 val draft=SaleDraft("client","1",listOf(SaleLine("local","a",null,"A",n("1"),n("100"),n("60"))),listOf(SalePayment("drawer","USD",n("100"),"USD",n("1"),n("100"))))
 val id=engine.save(draft,SalesEngine(android.content.Context()).calculate(draft));val q=RoomSalesEngine.ExchangeRequest("rollback",id,d.lines.values.first().id,n("1"),SaleLine("local","b",null,"B",n("1"),n("80"),n("40")),"drawer","USD",n("1"),n("0"),"test")
 val p=engine.previewExchange(q);val before=d.snapshot();d.failStock=true;check(runCatching{engine.exchange(q,p.fingerprint)}.isFailure);check(before==d.snapshot());assertions+=2
 d.failStock=false;engine.exchange(q,p.fingerprint)
 val replacement=d.lines.values.first{it.product_id=="b"};engine.returnLine(id,replacement.id,n("1"),"return replacement","drawer","USD");eq(profit(d).toPlainString(),"0");eq(d.sales.getValue(id).paid,"0");eq(d.debts.getValue(id).balance,"0");assertions+=3
 // Repeated replacement, partial returns, late collection and stale preview.
 for(channel in listOf("store","online")){
  val x=AppDatabase();AppDatabase.instance=x;val e=RoomSalesEngine(android.content.Context())
  x.stock["local/a/null"]="5";x.stock["local/b/null"]="5"
  val draft2=SaleDraft("c","1",listOf(SaleLine("local","a",null,"A",n("2"),n("50"),n("20"))),listOf(SalePayment("drawer","USD",n("30"),"USD",n("1"),n("30"))),discount=n("10"),channel=channel)
  val s=e.save(draft2,SalesEngine(android.content.Context()).calculate(draft2));val original=x.lines.values.first()
  val request=RoomSalesEngine.ExchangeRequest("repeat",s,original.id,n("1"),SaleLine("local","b",null,"B",n("1"),n("60"),n("25")),"drawer","USD",n("1"),n("20"),"test")
  val preview=e.previewExchange(request);e.collect(s,SalePayment("drawer","USD",n("5"),"USD",n("1"),n("5")))
  check(runCatching{e.exchange(request,preview.fingerprint)}.isFailure)
  e.exchange(request,e.previewExchange(request).fingerprint)
  eq(x.debts.getValue(s).balance,"50")
  e.collect(s,SalePayment("drawer","USD",n("50"),"USD",n("1"),n("50")))
  eq(profit(x).toPlainString(),"60")
  e.returnLine(s,original.id,n("1"),"return remaining","drawer","USD")
  eq(profit(x).toPlainString(),"35");eq(x.sales.getValue(s).paid,"60")
  val b=x.lines.values.first{it.product_id=="b"}
  val second=request.copy(requestId="second",lineId=b.id,replacement=SaleLine("local","a",null,"A",n("1"),n("40"),n("20")),collect=n("0"))
  e.exchange(second,e.previewExchange(second).fingerprint);eq(profit(x).toPlainString(),"20");eq(x.sales.getValue(s).paid,"40")
  e.cancelWithRefund(s);eq(profit(x).toPlainString(),"0");assertions+=8
 }
 for(channel in listOf("store","online")){
  val manualDb=AppDatabase()
  AppDatabase.instance=manualDb
  val manualEngine=RoomSalesEngine(android.content.Context())
  val sale=draft.copy(channel=channel,lines=listOf(SaleLine(MANUAL_SALE_SOURCE,"manual-test",null,"سوار",n("2"),n("50"),n("30"))))
  val manualId=manualEngine.save(sale,SalesEngine(android.content.Context()).calculate(sale))
  eq(profit(manualDb).toPlainString(),"40")
  manualEngine.returnLine(manualId,manualDb.lines.values.single().id,n("1"),"مرتجع","drawer","USD")
  eq(profit(manualDb).toPlainString(),"20")
  manualEngine.cancelWithRefund(manualId)
  eq(profit(manualDb).toPlainString(),"0")
  check(manualDb.stock.isEmpty())
  assertions+=4
 }
 val archivedSale=org.json.JSONObject("""{"id":"s","saleType":"online","total":100,"paid":30}""")
 val oldReceipt=org.json.JSONObject("""{"referenceType":"sale","referenceId":"s","amount":40,"onlineCollectionDiscount":5}""")
 val oldReturn=org.json.JSONObject("""{"referenceType":"sale","referenceId":"s","itemIndex":1,"qty":1,"refundAmount":10,"creditAmount":25}""")
 val archive=ArchivedSaleBalance.calculate(archivedSale,listOf(oldReceipt),listOf(oldReturn),n("3"))
 eq(archive.effective.toPlainString(),"75");eq(archive.paid.toPlainString(),"63");eq(archive.due.toPlainString(),"7")
 eq(archive.returnedByLine.getValue(1).toPlainString(),"1");assertions+=4
 for(paid in listOf("80","100")){
  val db=AppDatabase();AppDatabase.instance=db
  val ctx=android.content.Context()
  val raw=org.json.JSONObject("""{"id":"old","saleType":"store","total":100,"paid":$paid,"items":[{"productId":"a","name":"A","qty":1,"price":100,"cost":60}]}""")
  db.oldSales["old"]=LegacySaleEntity("old","src",12,0,"client","","USD","100",paid,"0","confirmed","",raw.toString())
  db.stock["src/a/null"]="0";db.stock["local/b/null"]="2"
  val ex=ArchivedSaleExchange(ctx)
  val req=RoomSalesEngine.ExchangeRequest("key","old","old#0",n("1"),SaleLine("local","b",null,"B",n("1"),n("90"),n("50")),"drawer","USD",n("1"),n("0"),"exchange")
  val preview=ex.preview(req,false)
  ex.save(req,preview.fingerprint);ex.save(req,preview.fingerprint)
  eq(db.stock.getValue("src/a/null"),"1");eq(db.stock.getValue("local/b/null"),"1")
  eq(db.debts.getValue("old").balance,if(paid=="80")"10" else "0")
  eq(db.cash.sumOf{n(it.amount)}.toPlainString(),if(paid=="100")"-10" else "0")
  val b=SalesRoomRepository(ctx).archivedBalance(db.oldSales.getValue("old"))
  eq(b.effective.toPlainString(),"90");eq(b.paid.toPlainString(),if(paid=="80")"80" else "90")
  check(runCatching{ex.preview(req.copy(requestId="another"),false)}.isFailure);assertions+=7
 }
 run {
  val db=AppDatabase();AppDatabase.instance=db;val ctx=android.content.Context()
  val raw="""{"id":"old","saleType":"online","total":100,"paid":100,"items":[{"productId":"a","name":"A","qty":1,"price":100,"cost":60}]}"""
  db.oldSales["old"]=LegacySaleEntity("old","src",12,0,"client","","USD","100","100","0","confirmed","",raw)
  db.stock["src/a/null"]="0"
  val ex=ArchivedSaleExchange(ctx)
  val req=RoomSalesEngine.ExchangeRequest("key","old","old#0",n("1"),SaleLine("src","a",null,"A",n("1"),n("110"),n("60")),"drawer","TRY",n("0.02"),n("500"),"price")
  ex.save(req,ex.preview(req,false).fingerprint)
  eq(db.stock.getValue("src/a/null"),"0");eq(org.json.JSONObject(db.oldSales.getValue("old").original_json).archiveExchanges().single().getString("nativeProfitDeltaUsd"),"10");assertions++;eq(db.cash.single().amount,"500");eq(db.debts.getValue("old").balance,"0")
  eq(SalesRoomRepository(ctx).archivedBalance(db.oldSales.getValue("old")).paid.toPlainString(),"110");assertions+=4
 }
 println("PASS $assertions financial assertions using production RoomSalesEngine and in-memory boundary doubles (not Room/SQLite integration).")
}
