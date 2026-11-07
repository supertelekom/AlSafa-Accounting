package com.alsafa.native8.sales
import android.content.Context
class AppDatabase {
 companion object {var instance=AppDatabase();fun get(c:Context)=instance}
 val sales=linkedMapOf<String,RoomSale>();val lines=linkedMapOf<String,RoomSaleLine>();val debts=linkedMapOf<String,RoomReceivable>();val payments=mutableListOf<RoomSalePayment>();val cash=mutableListOf<RoomCashboxMovement>();val returns=mutableListOf<RoomSaleReturn>();val profits=mutableListOf<SaleProfitEvent>();val reversals=mutableListOf<SaleProfitReversal>();val refunds=mutableListOf<SaleRefundAllocation>();val store=mutableListOf<StoreSaleProfitEvent>();val adjustments=mutableListOf<StoreProfitAdjustment>();val stock=linkedMapOf<String,String>();var failStock=false
 data class Snapshot(val sales:Map<String,RoomSale>,val lines:Map<String,RoomSaleLine>,val debts:Map<String,RoomReceivable>,val payments:List<RoomSalePayment>,val cash:List<RoomCashboxMovement>,val returns:List<RoomSaleReturn>,val profits:List<SaleProfitEvent>,val reversals:List<SaleProfitReversal>,val refunds:List<SaleRefundAllocation>,val store:List<StoreSaleProfitEvent>,val adjustments:List<StoreProfitAdjustment>,val stock:Map<String,String>)
 fun snapshot()=Snapshot(sales.toMap(),lines.toMap(),debts.toMap(),payments.toList(),cash.toList(),returns.toList(),profits.toList(),reversals.toList(),refunds.toList(),store.toList(),adjustments.toList(),stock.toMap())
 fun restore(s:Snapshot){sales.clear();sales.putAll(s.sales);lines.clear();lines.putAll(s.lines);debts.clear();debts.putAll(s.debts);payments.clear();payments.addAll(s.payments);cash.clear();cash.addAll(s.cash);returns.clear();returns.addAll(s.returns);profits.clear();profits.addAll(s.profits);reversals.clear();reversals.addAll(s.reversals);refunds.clear();refunds.addAll(s.refunds);store.clear();store.addAll(s.store);adjustments.clear();adjustments.addAll(s.adjustments);stock.clear();stock.putAll(s.stock)}
 fun salesPostingDao()=Posting(this);fun legacyPaymentDao()=PaymentDao(this);fun legacyReceivablesDao()=DebtDao(this);fun salesFinancialAuditDao()=Audit(this);fun storeSaleProfitDao()=Store(this);fun legacySaleDao()=Legacy();fun maintenanceDao()=Maintenance()
}
class Posting(val d:AppDatabase){suspend fun sale(id:String)=d.sales[id];suspend fun lines(id:String)=d.lines.values.filter{it.sale_id==id};suspend fun line(s:String,l:String)=d.lines[l]?.takeIf{it.sale_id==s};suspend fun maxNewNumber()=d.sales.values.maxOfOrNull{it.number} ?: 0L
 suspend fun insertSale(s:RoomSale){check(d.sales.putIfAbsent(s.id,s)==null)};suspend fun insertLine(l:RoomSaleLine){check(d.lines.putIfAbsent(l.id,l)==null)};suspend fun insertReturn(r:RoomSaleReturn){d.returns+=r}
 suspend fun updateReturned(id:String,q:String):Int{d.lines[id]=d.lines.getValue(id).copy(returned_qty=q);return 1}
 suspend fun updatePayment(id:String,p:String,s:String):Int{d.sales[id]=d.sales.getValue(id).copy(paid=p,status=s);return 1}
 suspend fun updateExchange(id:String,t:String,m:String):Int{d.sales[id]=d.sales.getValue(id).copy(total=t,metadata_json=m);return 1}
 suspend fun updateCustomer(id:String,c:String,p:String):Int{d.sales[id]=d.sales.getValue(id).copy(customer=c,phone=p);return 1}
 suspend fun correctUnpaidLine(s:String,l:String,q:String,p:String):Int{d.lines[l]=d.lines.getValue(l).copy(quantity=q,unit_price=p);return 1}
 suspend fun correctUnpaidTotal(id:String,t:String,discount:String):Int{d.sales[id]=d.sales.getValue(id).copy(total=t,discount=discount);return 1}
}
class PaymentDao(val d:AppDatabase){suspend fun payments(s:String)=d.payments.filter{it.sale_id==s};suspend fun cashboxMovements(s:String)=d.cash.filter{it.sale_id==s};suspend fun insertPayment(p:RoomSalePayment){d.payments+=p};suspend fun insertCashboxMovement(m:RoomCashboxMovement){d.cash+=m}}
class DebtDao(val d:AppDatabase){suspend fun bySaleId(id:String)=d.debts[id];suspend fun insert(r:RoomReceivable){check(d.debts.putIfAbsent(r.sale_id,r)==null)};suspend fun compareAndSetBalance(id:String,e:String,n:String):Int{if(d.debts[id]?.balance!=e)return 0;d.debts[id]=d.debts.getValue(id).copy(balance=n);return 1};suspend fun updateCustomer(id:String,c:String,p:String):Int{d.debts[id]=d.debts.getValue(id).copy(customer=c,phone=p);return 1}}
class Audit(val d:AppDatabase){suspend fun profits(id:String)=d.profits.filter{it.sale_id==id};suspend fun reversals(id:String)=d.reversals.filter{it.sale_id==id};suspend fun refunds(id:String)=d.refunds.filter{it.sale_id==id};suspend fun insertProfit(e:SaleProfitEvent){d.profits+=e};suspend fun insertReversal(e:SaleProfitReversal){d.reversals+=e};suspend fun insertRefund(e:SaleRefundAllocation){d.refunds+=e}}
class Store(val d:AppDatabase){suspend fun insert(e:StoreSaleProfitEvent){d.store+=e};suspend fun insertAdjustment(e:StoreProfitAdjustment){d.adjustments+=e};suspend fun bySale(id:String)=d.store.find{it.sale_id==id};suspend fun adjustments(id:String)=d.adjustments.filter{it.sale_id==id}}
data class Old(val due:String,val original_json:String="{}",val invoice_number:Long=1,val currency:String="USD",val total:String="0",val historical_paid:String="0",val status:String="",val customer:String="",val phone:String="",val created_at:Long=0)
data class UnifiedSaleRow(val id:String,val number:Long,val created_at:Long,val customer:String,val phone:String,val total:String,val paid:String,val status:String,val historical:Int,val channel:String?,val original_json:String?)
class Legacy{suspend fun maxLegacyInvoiceNumber()=0L;suspend fun legacyLines(id:String)=emptyList<RoomLegacySaleLine>();suspend fun legacyById(id:String):Old?=null;suspend fun unifiedArchive(s:String,f:Long,t:Long,l:Int,o:Int)=emptyList<UnifiedSaleRow>()}
class Maintenance{suspend fun maxRepairInvoice()=0L}
object UnifiedCashboxBalance{fun displayName(s:String)=s;fun canonical(s:String):String?=s.takeIf{it in setOf("drawer","external","bank","shamcash")}}
class ExchangeRateSettings(c:Context){fun rate(f:String,t:String)=when(f){"TRY"->java.math.BigDecimal("0.02");else->java.math.BigDecimal.ONE}}
object LegacySaleChannel{fun fromOriginalJson(s:String)=SalesChannelProfit.Channel.STORE}
object SalesChannelProfit{enum class Channel{STORE,ONLINE}}
