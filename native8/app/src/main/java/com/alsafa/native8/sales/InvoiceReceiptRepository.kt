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
    val p=parts.getJSONObject(i);row("قطعة الصيانة","${p.optString("name")} × ${p.optString("qty")} — ${p.optString("price")} ${r.currency}")
   }
   if(raw.has("laborPrice"))row("أجرة العمل",raw.optString("laborPrice")+" "+r.currency)
   row("الضريبة",raw.optString("tax","0")+" "+r.currency);row("حالة الصيانة",r.status)
   if(r.source_sha256=="native8") {
    row("الإجمالي", "${MaintenanceReturnRules.effective(raw)} ${r.currency}")
    row("المقبوض الصافي", "${MaintenanceReturnRules.paid(raw)} ${r.currency}")
    row("المتبقي", "${MaintenanceReturnRules.due(raw)} ${r.currency}")
   } else {
    row("قيمة الصيانة الأصلية","${LegacyMaintenanceProfitReader.total(raw)} ${r.currency}")
    row("المقبوض المسجل",raw.optString("received","0")+" "+r.currency)
    val remaining=if(raw.has("nativeArchiveDelivery"))BigDecimal.ZERO else com.alsafa.native8.maintenance.ArchivedRepairDeliveryRepository(db,context).balance(r.id)
    row("المتبقي", "$remaining ${r.currency}")
    raw.optJSONObject("nativeArchiveDelivery")?.optJSONObject("payment")?.let { p ->
     row("تحصيل التسليم","${p.optString("paymentAmount")} ${p.optString("paymentCurrency")}")
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
   val currency=old?.currency ?: "USD"
   val total=current?.total ?: old!!.total;val paid=current?.paid ?: old!!.historical_paid
   row("رقم الفاتورة",number);row("حالة الفاتورة",current?.status ?: old!!.status)
   row("التاريخ والوقت",Instant.ofEpochMilli(current?.created_at ?: old!!.created_at).atZone(ZoneId.systemDefault()).toLocalDateTime().toString())
   row("إسم العميل / الشركة",(current?.customer ?: old!!.customer).ifBlank{"عميل نقدي"})
   for(line in RoomSalesEngine(context).lines(id)) {
    row("نوع المنتج",line.title);row("سعر المنتج الإفرادي",line.price+" "+currency);row("عدد المنتجات",line.quantity)
    if((line.returned.toBigDecimalOrNull() ?: BigDecimal.ZERO)>BigDecimal.ZERO)row("المرتجع",line.returned)
   }
   val discount=current?.discount ?: JSONObject(old!!.original_json).optString("discount","0")
   row("الخصم",discount+" "+currency)
   row("الإجمالي الأصلي بعد الخصم",total+" "+currency);row("المقبوض الصافي",paid+" "+currency)
   row("المتبقي",(old?.due ?: db.legacyReceivablesDao().bySaleId(id)?.balance ?: "0")+" "+currency)
   for(p in db.legacyPaymentDao().payments(id))row("القبض الفعلي",p.amount+" "+p.currency+" — "+when(p.cashbox){"drawer"->"الدرج";"shamcash"->"شام كاش";"external"->"الصندوق الخارجي";else->p.cashbox})
   InvoiceReceipt("فاتورة مبيع",number,current?.phone ?: old!!.phone,rows)
  }
 }
}
