package com.alsafa.native8.maintenance

import android.view.View
import android.widget.*
import com.alsafa.native8.stylekit.*
import com.alsafa.native8.sales.LegacyMaintenanceProfitReader
import org.json.JSONObject
import com.alsafa.native8.sales.InvoiceDisplay

/** detailModal('repair') fields, using the shared Tajawal/cream/gold native presentation. */
class MaintenanceDetailPage(ui:SafaWidgets,repair:MaintenanceRepairEntity,
 onBack:()->Unit,onMessage:()->Unit,onEdit:()->Unit,onDelivery:()->Unit,onReturn:()->Unit,
 onPrint:()->Unit,onMetadataEdit:()->Unit,onSave:(String,String,String,String,Button)->Unit) {
 val host=SafaPageHost(ui,"الصفا للساعات");val view:View get()=host.view
 init {
  val raw=JSONObject(repair.original_json);val body=host.body
  ui.heading(body,"تفاصيل طلب الصيانة","فاتورة رقم ${repair.invoice_number} — ${repair.status}")
  ui.add(body,ui.button("رجوع",onClick=onBack))
  val details=ui.card();ui.add(body,details)
  fun row(label:String,value:String) {
   if(value.isBlank())return
   val line=LinearLayout(ui.context).apply {layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(0,ui.style.dp(9),0,ui.style.dp(9))}
   line.addView(ui.text(label,13f,SafaStyle.Palette.muted),LinearLayout.LayoutParams(0,-2,0.4f))
   line.addView(ui.text(value,16f,bold=true),LinearLayout.LayoutParams(0,-2,0.6f))
   ui.add(details,line,3)
   ui.add(details,View(ui.context).apply{setBackgroundColor(SafaStyle.Palette.line);minimumHeight=ui.style.dp(1)},3)
  }
  fun field(label:String,key:String){if(!raw.isNull(key))row(label,if(key in setOf("cost","laborPrice","partsTotal","partsCost","manualCost","tax","received"))InvoiceDisplay.amount(raw.optString(key)) else raw.optString(key))}
  row("رقم الفاتورة",repair.invoice_number.toString());row("العميل",repair.customer.ifBlank{"عميل نقدي"})
  row("الهاتف",repair.phone);row("التاريخ والوقت",repair.date+" "+raw.optString("time"))
  row("الساعة",repair.watch);field("الموديل","model");row("العطل",repair.problem)
  field("نتيجة الفحص","diagnosis");field("العمل المنجز","workDone");row("حالة الطلب",repair.status)
  field("خدمة الصيانة السريعة","quickServiceName")
  field("كلفة الصيانة","cost");field("أجرة العمل","laborPrice");field("قيمة القطع","partsTotal");field("تكلفة القطع","partsCost");field("تكلفة إضافية","manualCost");field("الضريبة","tax")
  val total=LegacyMaintenanceProfitReader.total(raw)
  row("الإجمالي", "${InvoiceDisplay.amount(total)} ${repair.currency}")
  if(repair.source_sha256=="native8") {
   row("الإجمالي بعد المرتجعات", "${InvoiceDisplay.amount(MaintenanceReturnRules.effective(raw))} ${repair.currency}")
   row("المقبوض الصافي", "${InvoiceDisplay.amount(MaintenanceReturnRules.paid(raw))} ${repair.currency}")
   row("المتبقي", "${InvoiceDisplay.amount(MaintenanceReturnRules.due(raw))} ${repair.currency}")
  } else {
   field("المقبوض المسجل","received")
   raw.optJSONObject("nativeArchiveDelivery")?.optJSONObject("payment")?.let {p ->
    row("تحصيل التسليم", "${InvoiceDisplay.amount(p.optString("paymentAmount"))} ${p.optString("paymentCurrency")}")
    row("تاريخ التحصيل",p.optString("date"));row("المتبقي بعد التسليم","0 ${repair.currency}")
   }
  }
  field("طريقة الدفع","paymentMethod");field("حالة الدفع","paymentStatus")
  field("تاريخ التسليم","deliveredDate");field("وقت التسليم","deliveredTime");field("ملاحظات","notes")
  raw.optJSONArray("usedParts")?.let {parts -> for(i in 0 until parts.length()) {
   parts.optJSONObject(i)?.let {p -> row("قطعة الصيانة", "${p.optString("name")} × ${p.optString("qty",p.optString("quantity","1"))} — ${InvoiceDisplay.amount(p.optString("price"))} ${repair.currency}")}
  } }
  raw.optString("watchPhoto").takeIf{it.isNotBlank()}?.let {photo ->
   ui.add(body,ImageView(ui.context).apply{adjustViewBounds=true;maxHeight=ui.style.dp(260);scaleType=ImageView.ScaleType.FIT_CENTER;MaintenancePhoto.display(this,photo)})
  }
  val photoCount=listOf("photos","watchPhotos","images").sumOf{raw.optJSONArray(it)?.length() ?: 0}
  if(photoCount>0)ui.add(body,ui.text("صور الصيانة المحفوظة: $photoCount — عرض الصور الإضافية قيد الربط"))
  val actions=SafaGrid(ui.context,{if(it>=650)3 else 2})
  fun action(label:String,run:()->Unit){actions.addView(ui.button(label,onClick=run))}
  action("تعديل بيانات الفاتورة",onMetadataEdit)
  action("واتساب أعمال",onMessage);action("فاتورة PDF / طباعة",onPrint)
  if(repair.source_sha256=="native8") {
   if(repair.status!="تم التسليم")action("تعديل الفاتورة",onEdit)
   if(MaintenanceDeliveryEligibility.allowed(repair.status,raw))action("تم التسليم — تحصيل المبلغ",onDelivery)
   if(repair.status=="تم التسليم")action("مرتجع / استبدال",onReturn)
  }
  ui.add(body,actions)
  if(repair.source_sha256=="native8" && repair.status!="تم التسليم") {
   val edit=ui.card();ui.add(body,edit);ui.heading(edit,"تحديث حالة الصيانة","")
   val states=listOf("قيد الصيانة","بانتظار القطعة","جاهز للاستلام")
   val status=ui.choice(states.map{it to it},repair.status.takeIf{it in states} ?: states.first(),true){}
   ui.add(edit,status.view)
   fun input(label:String,key:String):EditText {ui.label(edit,label);return ui.input(multiline=true).apply{setText(raw.optString(key));ui.add(edit,this)}}
   val diagnosis=input("التشخيص","diagnosis");val work=input("العمل المنجز","workDone");val notes=input("ملاحظات","notes")
   lateinit var save:Button
   save=ui.button("حفظ تحديث الحالة",true){onSave(status.value,diagnosis.text.toString(),work.text.toString(),notes.text.toString(),save)}
   ui.add(edit,save)
  }
 }
}
