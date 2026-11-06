package com.alsafa.native8.home

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import com.alsafa.native8.sales.*
import com.alsafa.native8.data.migration.LegacyStagingDatabase
import org.json.JSONObject
import java.io.StringReader
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Home-only, read-only projections. Never writes stock, receipts or historical balances. */
class HomeReadRepository(private val context:Context) {
 private val db=AppDatabase.get(context)
 private val catalog=SalesProductCatalog(context)
 fun close(){catalog.close()}
 fun settings():JSONObject {
  val staging=LegacyStagingDatabase(context)
  try {val reader=com.alsafa.native8.data.migration.LegacyReadRepository(staging);val run=reader.latestRunId() ?: return JSONObject();return JSONObject(reader.objectSection(run,"settings") ?: "{}")}
  finally {staging.close()}
 }
 data class Row(val title:String,val detail:String,val route:String,val id:String="")
 data class Summary(val carry:Map<String,BigDecimal>,val sales:Map<String,BigDecimal>,val repairs:Map<String,BigDecimal>,val warning:String)
 private fun fields(raw:String):Map<String,String> {
  val result=mutableMapOf<String,String>()
  val keys=setOf("name","stock","minStock","model","barcode","currency","salePrice","phone","customer","price","received","status")
  JsonReader(StringReader(raw)).use { r -> r.beginObject();while(r.hasNext()) {
   val key=r.nextName();if(key in keys && r.peek() in setOf(JsonToken.STRING,JsonToken.NUMBER))result[key]=r.nextString() else r.skipValue()
  };r.endObject() };return result
 }
 private fun decimal(s:String?)=s?.toBigDecimalOrNull() ?: BigDecimal.ZERO
 suspend fun summary():Summary {
  val today=LocalDate.now();val from=today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
  val until=today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
  val carry=mutableMapOf<String,BigDecimal>();val totals=HomeIncomeTotals()
  val nativeClosing=db.dailyClosingDao().previous(today.toString())
  var closingDate=nativeClosing?.date.orEmpty()
  nativeClosing?.let { val raw=JSONObject(it.drawer_next_json);listOf("USD","SYP","TRY").forEach { c ->carry[c]=decimal(raw.optString(c)) } }
  val nativeIds=db.supplierPurchaseDao().operationalCashMovementIds().toHashSet()
  var invalid=0
  val staging=LegacyStagingDatabase(context)
  try {
   staging.readableDatabase.rawQuery("SELECT json FROM legacy_records WHERE run_id=(SELECT MAX(id) FROM import_runs) AND section='dailyClosings'",null).use { c ->while(c.moveToNext()) {
    val raw=JSONObject(c.getString(0));val date=raw.optString("date")
    if(date<today.toString() && date>closingDate) { closingDate=date;val values=raw.optJSONObject("drawerNext") ?: JSONObject();listOf("USD","SYP","TRY").forEach { carry[it]=decimal(values.optString(it)) } }
   } }
   val seen=hashSetOf<String>()
   staging.readableDatabase.rawQuery("SELECT json FROM legacy_records WHERE run_id=(SELECT MAX(id) FROM import_runs) AND section='cashTransactions'",null).use { c ->while(c.moveToNext()) {
    val raw=JSONObject(c.getString(0));if(raw.optString("date")!=today.toString() || raw.optString("type")!="income")continue
    val id=raw.optString("id");if(id in nativeIds || !seen.add(id))continue
    val amount=raw.optString("amount").toBigDecimalOrNull();if(amount==null){invalid++;continue}
    totals.add(id,raw.optString("referenceType"),raw.optString("currency"),amount)
   } }
  } finally {staging.close()}
  db.supplierPurchaseDao().cashMovementsBetween(from-1,until-1).filter { decimal(it.amount)>BigDecimal.ZERO }.forEach { row ->
   val kind=when {
    row.id.startsWith("repair-payment-") || row.id.startsWith("archive-repair-delivery:")->"repair"
    db.salesPostingDao().sale(row.sale_id)!=null || db.legacySaleDao().legacyById(row.sale_id)!=null ->"sale"
    else->null
   }
   if(kind!=null)totals.add(row.id,kind,row.currency,decimal(row.amount))
  }
  return Summary(carry,totals.sales,totals.repairs,if(invalid>0)"تعذر قراءة بعض تحصيلات اليوم" else "")
 }
 suspend fun products(query:String,lowOnly:Boolean):List<Row> {
  val dao=db.operationalInventoryWriteDao();val out=mutableListOf<Row>();val source=dao.latestSource()
  suspend fun add(raw:String,sourceId:String,id:String) {
   currentCoroutineContext().ensureActive()
   val f=fields(raw).toMutableMap()
   if(sourceId!="local")dao.edit(sourceId,id)?.let { f.putAll(fields(it.edited_json)) }
   var stock=decimal(f["stock"])
   if(sourceId!="local")stock+=dao.stockHistory(sourceId,id,null).fold(BigDecimal.ZERO){sum,r->sum+decimal(r.quantity_delta)}
   val matches=listOf(f["name"],f["model"],f["barcode"]).joinToString(" ").contains(query,true)
   if((!lowOnly && matches)||(lowOnly && stock<=decimal(f["minStock"])))out+=Row(f["name"].orEmpty(),"المخزون: ${stock.stripTrailingZeros().toPlainString()} · حد التنبيه: ${decimal(f["minStock"])} · ${f["model"].orEmpty()}","products")
  }
  if(source!=null){var n=0;while(true){val row=dao.productPage(source,1,n++).singleOrNull() ?: break;add(row.original_json,source,row.legacy_id);if(!lowOnly && out.size>=30)break}}
  var n=0;while(lowOnly || out.size<30){val row=dao.searchLocalPage(n++).singleOrNull() ?: break;add(row.product_json,"local",row.local_id)}
  if(lowOnly)db.maintenanceDao().parts().forEach { p ->val f=fields(p.original_json);if(decimal(p.stock)<=decimal(f["minStock"]))out+=Row(p.name,"قطع الصيانة · المخزون: ${p.stock} · حد التنبيه: ${decimal(f["minStock"])}","parts") }
  return out
 }
 suspend fun search(query:String):List<Row> {
  if(query.isBlank())return emptyList()
  val sales=db.legacySaleDao().unifiedArchive(query,limit=30).map { Row("مبيع #${it.number} — ${it.customer}",it.phone,"sale_detail",it.id) }
  val repairs=db.maintenanceDao().search(query,30).map {Row("صيانة #${it.invoice_number} — ${it.customer}","${it.watch} · ${it.status} · ${it.phone}","repair_detail",it.id)}
  val products=catalog.find(query).map { Row(it.title,"المخزون: ${it.available} · ${InvoiceDisplay.amount(it.price)} ${it.currency}","products") }
  return sales+repairs+products
 }
 suspend fun debts(query:String):List<Row> {
  val result=db.legacyReceivablesDao().searchOutstanding(query).filter {decimal(it.balance)>BigDecimal("0.009")}.map { debt ->
  val old=db.legacySaleDao().legacyById(debt.sale_id)
  val sale=db.salesPostingDao().sale(debt.sale_id)
  Row("${debt.customer} — فاتورة ${old?.invoice_number ?: sale?.number ?: ""}","${debt.phone} · المتبقي ${InvoiceDisplay.amount(debt.balance)} ${old?.currency ?: "USD"}","sale_detail",debt.sale_id)
 }.toMutableList()
  val staging=LegacyStagingDatabase(context)
  try {
   val source=com.alsafa.native8.data.migration.LegacyReadRepository(staging)
   val paymentCache=mutableMapOf<String,Pair<List<JSONObject>,List<JSONObject>>>()
   db.openHelper.readableDatabase.query("SELECT id,source_sha256,invoice_number,customer,phone,currency,original_json FROM maintenance_repairs WHERE customer LIKE ? OR phone LIKE ? OR CAST(invoice_number AS TEXT) LIKE ?",arrayOf("%$query%","%$query%","%$query%")).use { c ->while(c.moveToNext()) {
    currentCoroutineContext().ensureActive()
    val raw=JSONObject(c.getString(6));val origin=c.getString(1)
    val due=if(origin=="native8")com.alsafa.native8.maintenance.MaintenanceReturnRules.due(raw)
    else if(raw.has("nativeArchiveDelivery"))BigDecimal.ZERO else {
     val linked=paymentCache.getOrPut(origin) {
      val run=staging.readableDatabase.rawQuery("SELECT id FROM import_runs WHERE source_sha256=?",arrayOf(origin)).use {r -> check(r.moveToFirst());r.getLong(0)}
      source.financialRows(run,"payments") to source.financialRows(run,"returns")
     };ArchivedRepairDeliveryRules.due(raw,linked.first,linked.second)
    }
    if(due>BigDecimal("0.009"))result+=Row("صيانة #${c.getLong(2)} — ${c.getString(3)}","${c.getString(4)} · المتبقي ${InvoiceDisplay.amount(due)} ${c.getString(5)}","repair_detail",c.getString(0))
   } }
  } finally {staging.close()}
  return result
 }
}
