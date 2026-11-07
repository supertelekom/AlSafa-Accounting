package com.alsafa.native8.sales

import android.content.Context
import com.alsafa.native8.maintenance.MaintenanceReturnRules
import org.json.JSONObject
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only receipt projection. No payment, inventory or financial events are written. */
class InvoiceReceiptRepository(private val context:Context) {
 suspend fun load(saleId:String?,repairId:String?):InvoiceReceipt=withContext(Dispatchers.IO) {
  val db=AppDatabase.get(context);val rows=mutableListOf<Pair<String,String>>()
  fun row(label:String,value:String){rows+=label to value}
  if(!repairId.isNullOrBlank()) {
   val r=db.maintenanceDao().repair(repairId) ?: error("الصيانة غير موجودة")
   val raw=JSONObject(r.original_json)
   row("رقم الفاتورة",r.invoice_number.toString());row("التاريخ والوقت",r.date+" "+raw.optString("time"));row("إسم العميل / الشركة",r.customer.ifBlank{"عميل"})
   row("الساعة",r.watch);row("العطل",r.problem)
   val parts=raw.optJSONArray("usedParts")
   if(parts==null || parts.length()==0)row("قطع الصيانة","—") else for(i in 0 until parts.length()) {
    val p=parts.getJSONObject(i);row("قطعة الصيانة","${p.optString("name")} × ${p.optString("qty")} — ${InvoiceDisplay.amount(p.optString("price"))} ${r.currency}")
   }
   if(raw.has("laborPrice"))row("أجرة العمل",InvoiceDisplay.amount(raw.optString("laborPrice"))+" "+r.currency)
   row("الضريبة",InvoiceDisplay.amount(raw.optString("tax","0"))+" "+r.currency);row("حالة الصيانة",r.status)
   if(r.source_sha256=="native8") {
    row("الإجمالي", "${InvoiceDisplay.amount(MaintenanceReturnRules.effective(raw))} ${r.currency}")
    row("المقبوض الصافي", "${InvoiceDisplay.amount(MaintenanceReturnRules.paid(raw))} ${r.currency}")
    row("المتبقي", "${InvoiceDisplay.amount(MaintenanceReturnRules.due(raw))} ${r.currency}")
   } else {
    row("قيمة الصيانة الأصلية","${InvoiceDisplay.amount(LegacyMaintenanceProfitReader.total(raw))} ${r.currency}")
    row("المقبوض المسجل",InvoiceDisplay.amount(raw.optString("received","0"))+" "+r.currency)
    val remaining=if(raw.has("nativeArchiveDelivery"))BigDecimal.ZERO else com.alsafa.native8.maintenance.ArchivedRepairDeliveryRepository(db,context).balance(r.id)
    row("المتبقي", "${InvoiceDisplay.amount(remaining)} ${r.currency}")
    raw.optJSONObject("nativeArchiveDelivery")?.optJSONObject("payment")?.let { p ->
     row("تحصيل التسليم","${InvoiceDisplay.amount(p.optString("paymentAmount"))} ${p.optString("paymentCurrency")}")
    }
   }
   row("طريقة الدفع",raw.optString("paymentMethod","نقدي"))
   InvoiceReceipt("فاتورة صيانة",r.invoice_number.toString(),r.phone,rows)
  } else {
   val id=saleId ?: error("معرف الفاتورة غير موجود")
   val current=db.salesPostingDao().sale(id)
   val old=if(current==null)db.legacySaleDao().legacyById(id) else null
   require(current!=null || old!=null){"الفاتورة غير موجودة"}
   val number=current?.number?.toString() ?: old!!.invoice_number.toString()
   val meta=current?.let{JSONObject(it.metadata_json)}
   val currency=old?.currency ?: meta!!.optString("currency","USD")
   val displayRate=meta?.optString("rateToUsd","1")?.toBigDecimal() ?: BigDecimal.ONE
   fun show(value:String)=InvoiceDisplay.amount(value.toBigDecimal().divide(displayRate,2,java.math.RoundingMode.HALF_UP))
   val total=current?.total ?: old!!.total;val paid=current?.paid ?: old!!.historical_paid
   row("رقم الفاتورة",number);row("حالة الفاتورة",current?.status ?: old!!.status)
   row("التاريخ والوقت",Instant.ofEpochMilli(current?.created_at ?: old!!.created_at).atZone(ZoneId.systemDefault()).toLocalDateTime().toString())
   row("إسم العميل / الشركة",(current?.customer ?: old!!.customer).ifBlank{"عميل نقدي"})
   for(line in RoomSalesEngine(context).lines(id)) {
    row("نوع المنتج",line.title);row("سعر المنتج الإفرادي",show(line.price)+" "+currency);row("عدد المنتجات",line.quantity)
    if((line.returned.toBigDecimalOrNull() ?: BigDecimal.ZERO)>BigDecimal.ZERO)row("المرتجع",line.returned)
   }
   val discount=current?.discount ?: JSONObject(old!!.original_json).optString("discount","0")
   row("الخصم",show(discount)+" "+currency)
   val due=db.legacyReceivablesDao().bySaleId(id)?.balance ?: old?.due ?: "0"
   val effective=if((current?.status ?: old?.status)=="ملغاة")"0" else if(current!=null)meta!!.optString("exchangeEffectiveTotal").ifBlank{(paid.toBigDecimal()+due.toBigDecimal()).toPlainString()} else total
   row("الإجمالي بعد التسويات",show(effective)+" "+currency);row("المقبوض الصافي",show(if(old!=null)(paid.toBigDecimal()+db.legacyPaymentDao().payments(id).fold(BigDecimal.ZERO){a,p->a+p.amount.toBigDecimal()}).toPlainString() else paid)+" "+currency)
   row("المتبقي",show(due)+" "+currency)
   if(old!=null){val raw=JSONObject(old.original_json);for((key,label) in listOf("warranty" to "الضمان","warrantyMonths" to "مدة الضمان بالأشهر","notes" to "ملاحظات"))raw.optString(key).takeIf{it.isNotBlank()&&it!="null"}?.let{row(label,it)}}
   if(meta!=null){val months=meta.optInt("warrantyMonths",0)
    row("مدة الضمان",if(months>0)"$months شهر" else "بدون ضمان مسجل")
    if(months>0)row("نهاية الضمان",Instant.ofEpochMilli(current!!.created_at).atZone(ZoneId.systemDefault()).toLocalDate().plusMonths(months.toLong()).toString())
    if(meta.optString("notes").isNotBlank())row("ملاحظات",meta.getString("notes"))
   }
   val tender=meta?.optJSONArray("tender")
   if(tender!=null)for(i in 0 until tender.length()){val p=tender.getJSONObject(i);row("القبض الفعلي",InvoiceDisplay.amount(p.getString("amount"))+" "+p.getString("currency")+" — "+UnifiedCashboxBalance.displayName(p.getString("cashbox")))}
   meta?.optJSONObject("change")?.let{row("الباقي للعميل",InvoiceDisplay.amount(it.getString("amount"))+" "+it.getString("currency")+" — "+UnifiedCashboxBalance.displayName(it.getString("cashbox")))}
   if(tender==null)for(p in db.legacyPaymentDao().payments(id))row("القبض الفعلي",InvoiceDisplay.amount(p.amount)+" "+p.currency+" — "+when(p.cashbox){"drawer"->"الدرج";"shamcash"->"شام كاش";"external"->"الصندوق الخارجي";else->p.cashbox})
   InvoiceReceipt("فاتورة مبيع",number,current?.phone ?: old!!.phone,rows)
  }
 }
}
