package com.alsafa.native8.sales

import android.content.Context
import com.alsafa.native8.data.migration.LegacyReadRepository
import com.alsafa.native8.data.migration.LegacyStagingDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Read-only documents: retain original monetary values, never replay their movements. */
data class ReturnDocument(val key:String,val title:String,val invoice:String,val referenceId:String,
 val repair:Boolean,val date:String,val item:String,val rows:List<Pair<String,String>>,val originalAvailable:Boolean) {
 fun receipt()=InvoiceReceipt(title,invoice,"",rows)
}
class ReturnHistoryRepository(private val context:Context){
 suspend fun load():List<ReturnDocument> = withContext(Dispatchers.IO){
  val db=AppDatabase.get(context);val documents=linkedMapOf<String,ReturnDocument>()
  suspend fun original(id:String,repair:Boolean):Pair<String,Boolean> {
   if(repair){val r=db.maintenanceDao().repair(id);return (r?.invoice_number?.toString() ?: "") to (r!=null)}
   val sale=db.salesPostingDao().sale(id);val old=if(sale==null)db.legacySaleDao().legacyById(id) else null
   return (sale?.number?.toString() ?: old?.invoice_number?.toString().orEmpty()) to (sale!=null||old!=null)
  }
  suspend fun append(raw:JSONObject,key:String,defaultId:String="",defaultInvoice:String=""){
   val id=raw.optString("referenceId").ifBlank{defaultId};val repair=raw.optString("referenceType")=="repair"
   val (number,available)=original(id,repair)
   val invoice=number.ifBlank{raw.optString("invoiceNo").ifBlank{defaultInvoice}.ifBlank{"—"}}
   val title=if(raw.optString("kind")=="exchange")"فاتورة استبدال" else "فاتورة مرتجع"
   val item=raw.optString("itemName").ifBlank{raw.optString("returnedPartName")}.ifBlank{raw.optString("notes")}
   val date=(raw.optString("date")+" "+raw.optString("time")).trim();val unit=raw.optString("currency").uppercase()
   val rows=mutableListOf("الفاتورة الأصلية" to invoice,"التاريخ والوقت" to date,"القسم" to if(repair)"الصيانة" else "المبيعات")
   fun text(key:String,label:String){raw.optString(key).takeIf{it.isNotBlank()&&it!="null"}?.let{rows+=label to it}}
   fun amount(key:String,label:String){raw.optString(key).toBigDecimalOrNull()?.let{rows+=label to (InvoiceDisplay.amount(it)+" "+unit)}}
   text("id","رقم العملية");text("itemName","الصنف المرتجع");text("returnedPartName","القطعة المسترجعة");text("qty","الكمية");text("returnedPartQty","كمية القطعة")
   text("replacementName","المنتج البديل");text("replacementPartName","القطعة البديلة");text("replacementQty","كمية البديل");text("replacementPartQty","كمية القطعة البديلة")
   amount(if(raw.has("returnedValue"))"returnedValue" else "creditAmount","قيمة المرتجع");amount(if(raw.has("replacementValue"))"replacementValue" else "replacementAmount","قيمة البديل");amount("creditAmount","صافي قيمة التسوية");amount("returnPenalty","تعويض للمحل");amount("refundAmount","المردود الفعلي");amount("extraCollected","المقبوض من الفرق");amount("remainingAfter","المتبقي بعد العملية")
   text("cashCurrency","عملة حركة الصندوق");text("cashRate","سعر الصرف إلى الدولار");text("cashCollected","المقبوض بعملة الصندوق");text("cashRefunded","المردود بعملة الصندوق");text("returnPenaltyReason","سبب التعويض");text("notes","ملاحظات");text("reason","السبب")
   raw.optString("vault").takeIf{it.isNotBlank()}?.let{rows+="الخزنة" to UnifiedCashboxBalance.displayName(it)}
   documents[key]=ReturnDocument(key,title,invoice,id,repair,date,item,rows,available)
  }
  val staging=LegacyStagingDatabase(context)
  try{val reader=LegacyReadRepository(staging);val run=reader.latestRunId()
   if(run!=null){var offset=0
    while(true){ensureActive();val page=reader.page(run,"returns",offset,500)
     for(row in page.rows){val raw=row.fields;val id=raw.optString("id").ifBlank{row.id ?: "row:${row.ordinal}"};append(raw,"legacy:$id")}
     offset+=page.rows.size;if(offset>=page.total)break;check(page.rows.isNotEmpty()){"تعذر إكمال سجل المرتجعات"}
    }
   }
  }finally{staging.close()}
  for(repair in db.maintenanceDao().nativeFinancialRecords()){
   ensureActive();val entries=JSONObject(repair.original_json).optJSONArray("nativeReturns") ?: continue
   for(i in 0 until entries.length()){val raw=entries.getJSONObject(i);append(raw,"repair:${repair.id}:${raw.optString("id",i.toString())}",repair.id,repair.invoice_number.toString())}
  }
  val exchangeReturns=mutableSetOf<String>()
  for(sale in db.salesPostingDao().exchangeInvoices()){
   val entries=JSONObject(sale.metadata_json).optJSONArray("nativeExchanges") ?: continue
   for(i in 0 until entries.length()){val raw=entries.getJSONObject(i);exchangeReturns+=raw.getString("returnId");append(raw,"exchange:${raw.getString("id")}",sale.id,sale.number.toString())}
  }
  for(r in db.legacySaleDao().allOperationalReturns()){
   if(r.id in exchangeReturns)continue
   ensureActive();val (number,available)=original(r.sale_id,false)
   val line=db.salesPostingDao().line(r.sale_id,r.line_id)
   val time=Instant.ofEpochMilli(r.created_at).atZone(ZoneId.systemDefault()).toLocalDateTime().withNano(0).toString().replace('T',' ')
   val rows=listOf("الفاتورة الأصلية" to number.ifBlank{"—"},"التاريخ والوقت" to time,"الصنف المرتجع" to (line?.title ?: "—"),"الكمية" to InvoiceDisplay.amount(r.quantity),"السبب" to r.reason)
   // sale_returns does not link a particular refund allocation. Do not infer cash from timestamps.
   documents["sale:${r.id}"]=ReturnDocument("sale:${r.id}","فاتورة مرتجع",number.ifBlank{"—"},r.sale_id,false,time,line?.title.orEmpty(),rows,available)
  }
  documents.values.sortedByDescending{it.date}
 }
 suspend fun receipt(key:String)=requireNotNull(load().firstOrNull{it.key==key}){"مستند المرتجع غير موجود"}.receipt()
}
