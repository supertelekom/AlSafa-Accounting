package com.alsafa.native8.sales
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.widget.*
import com.alsafa.native8.stylekit.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.math.BigDecimal

class SaleDetailsActivity:Activity(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private lateinit var ui:SafaWidgets;private var id="";private var busy=false
 override fun onCreate(state:Bundle?){super.onCreate(state);requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);ui=SafaWidgets(SafaStyle(this));id=intent.getStringExtra("sale_id").orEmpty()}
 override fun onResume(){super.onResume();load()}
 private fun open(type:Class<*>){startActivity(Intent(this,type).putExtra("sale_id",id))}
 private fun load(){val host=SafaPageHost(ui,"الصفا للساعات");val body=host.body;ui.heading(body,"تفاصيل فاتورة المبيع","");val error=ui.text("جارٍ التحميل…",14f,SafaStyle.Palette.danger);ui.add(body,error);setContentView(host.view)
  scope.launch{try{val db=AppDatabase.get(applicationContext)
   val current=withContext(Dispatchers.IO){db.salesPostingDao().sale(id)}
   val old=if(current==null)withContext(Dispatchers.IO){requireNotNull(db.legacySaleDao().legacyById(id)){"الفاتورة غير موجودة"}} else null
   val debt=withContext(Dispatchers.IO){db.legacyReceivablesDao().bySaleId(id)}
   val raw=old?.let{JSONObject(it.original_json)}
   val saleMeta=current?.let{JSONObject(it.metadata_json)}
   val unit=old?.currency ?: saleMeta!!.optString("currency","USD")
   val displayRate=saleMeta?.optString("rateToUsd","1")?.toBigDecimal() ?: BigDecimal.ONE
   fun display(n:BigDecimal)=InvoiceDisplay.amount(n.divide(displayRate,2,java.math.RoundingMode.HALF_UP))
   val value=(current?.total ?: old!!.total).toBigDecimal();val paid=(current?.paid ?: old!!.historical_paid).toBigDecimal();val due=debt?.balance?.toBigDecimal() ?: old?.due?.toBigDecimal() ?: (value-paid).max(BigDecimal.ZERO)
   val status=current?.status ?: old!!.status
   fun row(label:String,value:String){ui.add(body,ui.text("$label: $value",16f))}
   error.text="";row("رقم الفاتورة",current?.number?.toString() ?: raw!!.optString("invoiceNo"));row("العميل",current?.customer ?: raw!!.optString("customer"));row("الهاتف",current?.phone ?: raw!!.optString("phone"));row("الحالة",status)
   if(raw!=null)row("التاريخ",raw.optString("date")+" "+raw.optString("time"))
   if(current!=null){
    val stamp=java.time.Instant.ofEpochMilli(current.created_at).atZone(java.time.ZoneId.systemDefault())
    row("التاريخ والوقت",stamp.toLocalDate().toString()+" "+stamp.toLocalTime().withSecond(0).withNano(0))
    val meta=JSONObject(current.metadata_json);val months=meta.optInt("warrantyMonths",0)
    row("الضمان",if(months>0)"$months شهر — حتى ${stamp.toLocalDate().plusMonths(months.toLong())}" else "بدون ضمان مسجل")
    if(meta.optString("notes").isNotBlank())row("ملاحظات",meta.getString("notes"))
    meta.optJSONObject("change")?.let{row("الباقي المدفوع للعميل",InvoiceDisplay.amount(it.getString("amount"))+" "+it.getString("currency")+" — "+UnifiedCashboxBalance.displayName(it.getString("cashbox")))}
   }
   val card=ui.card();ui.add(card,ui.text("الإجمالي: ${display(value)} $unit",22f,bold=true));ui.add(card,ui.text("المقبوض: ${display(if(old!=null)value-due else paid)} $unit\nالمتبقي: ${display(due)} $unit",18f));ui.add(body,card)
   fun action(label:String,run:()->Unit){ui.add(body,ui.button(label){if(!busy)run()})}
   action("عرض الفاتورة / PDF / طباعة / مشاركة"){open(SalePrintActivity::class.java)}
   if(status!="ملغاة"&&due>BigDecimal.ZERO)action("تكملة الدفع"){open(SalePaymentsActivity::class.java)}
   if(old!=null){action("تعديل بيانات الفاتورة"){startActivity(Intent(this@SaleDetailsActivity,InvoiceMetadataActivity::class.java).putExtra("kind","sale").putExtra("invoice_id",id))}
    val items=raw!!.optJSONArray("items");for(i in 0 until (items?.length() ?: 0)){val r=items!!.optJSONObject(i) ?: continue;row(r.optString("name",r.optString("title",r.optString("productName","صنف"))),"${r.optString("qty",r.optString("quantity"))} × ${InvoiceDisplay.amount(r.optString("price",r.optString("unitPrice","0")))} $unit")}
   }else{
    action("تعديل اسم العميل ورقم الهاتف"){
     val form=ui.column(16);ui.label(form,"اسم العميل");val customer=ui.input().apply{setText(current!!.customer)};ui.add(form,customer);ui.label(form,"رقم الهاتف");val phone=ui.input().apply{setText(current!!.phone)};ui.add(form,phone)
     android.app.AlertDialog.Builder(this@SaleDetailsActivity).setTitle("تعديل العميل").setView(form).setNegativeButton("إلغاء",null).setPositiveButton("حفظ"){_,_->val c=customer.text.toString();val p=phone.text.toString();mutate(error){RoomSalesEngine(applicationContext).correctCustomer(id,c,p)}}.show()
    }
    val lines=withContext(Dispatchers.IO){RoomSalesEngine(applicationContext).lines(id)}
    lines.forEach{line->val item=ui.card();ui.add(item,ui.text(line.title,18f,bold=true));ui.add(item,ui.text("الكمية: ${line.quantity} — المرتجع: ${line.returned}\nالسعر: ${display(line.price.toBigDecimal())} $unit"))
     if(status!="ملغاة" && line.quantity.toBigDecimal()>line.returned.toBigDecimal())ui.add(item,ui.button("مرتجع هذا البند"){startActivity(Intent(this@SaleDetailsActivity,SaleReturnsActivity::class.java).putExtra("sale_id",id).putExtra("line_id",line.id))})
     if(status!="ملغاة" && paid.signum()==0)ui.add(item,ui.button("تعديل الكمية والسعر"){
      val form=ui.column(16);ui.label(form,"الكمية");val q=ui.input(numeric=true).apply{setText(line.quantity)};ui.add(form,q);ui.label(form,"سعر الوحدة — USD");val p=ui.input(numeric=true).apply{setText(line.price)};ui.add(form,p)
      android.app.AlertDialog.Builder(this@SaleDetailsActivity).setTitle("تعديل بند غير مقبوض").setView(form).setNegativeButton("إلغاء",null).setPositiveButton("حفظ"){_,_->val qty=q.text.toString().toBigDecimalOrNull();val price=p.text.toString().toBigDecimalOrNull();if(qty==null||price==null)error.text="أدخل قيماً صحيحة" else mutate(error){RoomSalesEngine(applicationContext).correctUnpaidLine(id,line.id,qty,price)}}.show()
     });ui.add(body,item)
    }
    val profit=withContext(Dispatchers.IO){val a=db.salesFinancialAuditDao();val earned=a.profits(id).fold(BigDecimal.ZERO){sum,r->sum+r.recognized_profit.toBigDecimal()};val reversed=a.reversals(id).fold(BigDecimal.ZERO){sum,r->sum+r.reversed_profit.toBigDecimal()};earned to reversed}
    row("الربح المسجل من القبض",InvoiceDisplay.amount(profit.first)+" USD")
    row("الربح المعكوس للمرتجعات والإلغاء",InvoiceDisplay.amount(profit.second)+" USD")
    row("صافي الربح المسجل من القبض",InvoiceDisplay.amount(profit.first-profit.second)+" USD")
    if(status!="ملغاة")action("إلغاء الفاتورة وتسوية المقبوض"){ui.confirm("إلغاء الفاتورة","سيتم إرجاع الكميات المتبقية وتسوية المقبوض وفق سجل الفاتورة. تأكيد الإلغاء؟"){mutate(error){RoomSalesEngine(applicationContext).cancelWithRefund(id)}}.show()}
   }
   action("رجوع"){finish()}
  }catch(e:CancellationException){throw e}catch(e:Exception){error.text=e.message ?: "تعذر فتح الفاتورة"}}
 }
 private fun mutate(error:TextView,operation:suspend ()->Unit){if(busy)return;busy=true;scope.launch{try{withContext(Dispatchers.IO){operation()};load()}catch(e:CancellationException){throw e}catch(e:Exception){error.text=e.message ?: "تعذر الحفظ"}finally{busy=false}}}
 override fun onDestroy(){scope.cancel();super.onDestroy()}
}
