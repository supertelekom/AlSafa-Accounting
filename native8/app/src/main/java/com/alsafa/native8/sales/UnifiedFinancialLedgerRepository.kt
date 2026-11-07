package com.alsafa.native8.sales

import com.alsafa.native8.data.migration.LegacyReadRepository
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant

/** Read-only chronological ledger. Origins are internal IDs, not separate customer-facing sections. */
class UnifiedFinancialLedgerRepository(private val db:AppDatabase,private val legacy:LegacyReadRepository) {
 suspend fun rows(from:String,to:String):List<FinancialLedgerPresentation.Row> {
  val first=LocalDate.parse(from);val last=LocalDate.parse(to);require(!last.isBefore(first))
  val run=legacy.latestRunId()
  fun originals(section:String)=if(run==null) emptyList() else legacy.financialRows(run,section)
  val sales=originals("sales");val repairs=originals("repairs");val payments=originals("payments");val returns=originals("returns")
  require(sales.map { it.optString("id") }.toSet().intersect(db.salesFinancialAuditDao().operationalSaleIds().toSet()).isEmpty()) { "تداخل في مراجع الفواتير؛ تعذر عرض قيود مكررة" }
  val exchanges=db.legacySaleDao().exchangedInvoices().flatMap{JSONObject(it.original_json).archiveExchanges()}
  val index=FinancialLedgerPresentation.index(sales,repairs,payments,returns+exchanges)
  val historicalSales=LegacyHistoricalSalesProfitRepository(db.legacySaleDao(),legacy).inspect()
  val historicalRepairs=LegacyMaintenanceProfitReader.extract(repairs,payments,returns)
  val rows=mutableListOf<FinancialLedgerPresentation.Row>()
  for(e in historicalSales.events+historicalRepairs.events) if(!e.recognizedOn.isBefore(first) && !e.recognizedOn.isAfter(last)) rows+=FinancialLedgerPresentation.event(e,index)
  val zone=ZoneId.of("Asia/Damascus");val w=FinancialPeriodGuard.window(from,to,zone)
  fun date(at:Long)=Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toString()
  val cache=mutableMapOf<String,Pair<String,String>>()
  suspend fun invoice(id:String):Pair<String,String> {
   cache[id]?.let { return it }
   val s=db.salesPostingDao().sale(id)
   val title=if(s==null) "تعذر العثور على تفاصيل الفاتورة" else "فاتورة رقم ${s.number} — ${s.customer.ifBlank { "عميل غير مسمى" }}"
   val detail=db.salesPostingDao().lines(id).joinToString("، ") { "${it.title} × ${it.quantity}" }
   return (title to detail).also { cache[id]=it }
  }
  suspend fun saleRow(key:String,id:String,at:Long,amount:String,kind:String) {
   val info=invoice(id);rows+=FinancialLedgerPresentation.Row(key,date(at),info.first,info.second,amount.toBigDecimal(),kind)
  }
  val compatibility=OriginalStoreProfitCompatibility.read(db.salesFinancialAuditDao(),w.endExclusive)
  for(e in db.storeSaleProfitDao().inPeriod(w.startInclusive,w.endExclusive)+compatibility.events.filter { it.recognized_at>=w.startInclusive }) saleRow("native:store:${e.sale_id}",e.sale_id,e.recognized_at,e.profit_usd,"ربح مبيع محل")
  for(e in db.storeSaleProfitDao().adjustmentsInPeriod(w.startInclusive,w.endExclusive)+compatibility.adjustments.filter { it.created_at>=w.startInclusive }) saleRow("native:adjustment:${e.id}",e.sale_id,e.created_at,e.profit_delta_usd,"مرتجع أو تسوية")
  for(e in db.salesFinancialAuditDao().verifiedOnlineProfitsInPeriod(w.startInclusive,w.endExclusive)) saleRow("native:online:${e.payment_id}",e.sale_id,e.collected_at,e.recognized_profit,"ربح تحصيل أونلاين")
  for(e in db.salesFinancialAuditDao().verifiedOnlineReversalsInPeriod(w.startInclusive,w.endExclusive)) saleRow("native:reversal:${e.id}",e.sale_id,e.reversed_at,(-e.reversed_profit.toBigDecimal()).toPlainString(),"مرتجع أو تسوية")
  val nativeRecords=db.maintenanceDao().nativeFinancialRecords()
  val nativeRows=(nativeRecords+db.maintenanceDao().allDelivered().filter { it.source_sha256!="native8" && JSONObject(it.original_json).has("nativeArchiveDelivery") }).map { JSONObject(it.original_json) }
  require(repairs.map { it.optString("id") }.toSet().intersect(nativeRecords.map { it.id }.toSet()).isEmpty()) { "تداخل في مراجع الصيانة" }
  fun embedded(key:String)=nativeRows.flatMap { r -> r.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty() }
  val nativeIndex=FinancialLedgerPresentation.index(emptyList(),nativeRows,embedded("nativePayments"),embedded("nativeReturns"))
  val native=NativeMaintenanceProfitRepository(db.maintenanceDao(),db.legacyPaymentDao()).read(to)
  for(e in native.audit.events) if(!e.recognizedOn.isBefore(first) && !e.recognizedOn.isAfter(last)) rows+=FinancialLedgerPresentation.event(e,nativeIndex)
  val costs=UnifiedExpensePartnerReadRepository(legacy,db.operationalExpensePartnerDao()).load(from,to,allowIncomplete=true)
  val names=mapOf("father" to "الأب","me" to "عبد الكريم","brother" to "يزن")
  for(e in costs.historicalEvidence) rows+=FinancialLedgerPresentation.Row("legacy:cost:${if(e.partnerId==null)"expense" else "withdrawal"}:${e.id}",e.date,
   if(e.partnerId==null)e.category else names[e.partnerId].orEmpty(),"${e.amount} ${e.currency} — ${e.notes}",-e.money.usd(),if(e.partnerId==null)"مصروف تشغيلي" else "سحب شريك (لا يخصم من الربح)")
  for(e in db.operationalExpensePartnerDao().expenses(from,to)) rows+=FinancialLedgerPresentation.Row("native:expense:${e.id}",e.date,e.category,"${e.amount} ${e.currency} — ${e.notes}",-(e.amount.toBigDecimal()*e.usd_per_unit.toBigDecimal()),"مصروف تشغيلي")
  for(e in db.operationalExpensePartnerDao().withdrawals(from,to)) rows+=FinancialLedgerPresentation.Row("native:withdrawal:${e.id}",e.date,names[e.partner_id] ?: "شريك غير معروف","${e.amount} ${e.currency} — ${e.notes}",-(e.amount.toBigDecimal()*e.usd_per_unit.toBigDecimal()),"سحب شريك (لا يخصم من الربح)")
  check(legacy.latestRunId()==run) { "تغيرت النسخة المستعادة أثناء القراءة" }
  return rows
 }
}
