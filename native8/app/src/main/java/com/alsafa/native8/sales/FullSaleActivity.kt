package com.alsafa.native8.sales

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.widget.*
import com.alsafa.native8.stylekit.*
import kotlinx.coroutines.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import org.json.JSONObject
import org.json.JSONArray
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

/** Full invoice editor, using the same transactional posting engine as quick sale. */
class FullSaleActivity:Activity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private lateinit var ui:SafaWidgets
 private lateinit var catalog:SalesProductCatalog
 private lateinit var customer:EditText;private lateinit var phone:EditText;private lateinit var discount:EditText
 private lateinit var cart:LinearLayout;private lateinit var receipts:LinearLayout;private lateinit var total:TextView;private lateinit var error:TextView
 private lateinit var search:EditText;private lateinit var results:LinearLayout
 private lateinit var channel:SafaWidgets.Dropdown
 private lateinit var saleUnit:SafaWidgets.Dropdown
 private var invoiceRate=BigDecimal.ONE
 private lateinit var dates:SafaDateTime.Fields;private lateinit var warranty:SafaWidgets.Dropdown
 private lateinit var notes:EditText;private lateinit var changeBox:SafaWidgets.Dropdown;private lateinit var changeUnit:SafaWidgets.Dropdown
 private lateinit var changePanel:LinearLayout;private lateinit var changePreview:TextView
 private val lines=mutableListOf<SaleLine>();private val payments=mutableListOf<SalePayment>()
 private var requestId=UUID.randomUUID().toString();private var saving=false;private var job:Job?=null
 private fun money(n:BigDecimal)=InvoiceDisplay.amount(n)
 override fun onCreate(state:Bundle?){super.onCreate(state);requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
  ui=SafaWidgets(SafaStyle(this));catalog=SalesProductCatalog(applicationContext);catalog.prepare()
  requestId=state?.getString("requestId") ?: requestId
  val host=SafaPageHost(ui,"الصفا للساعات");val body=host.body;ui.heading(body,"فاتورة مبيعات","اختر من المنتجات أو امسح الباركود بالكاميرا لإضافة الساعة")
  ui.step(body,1,"بيانات الفاتورة")
  val info=ui.card();ui.add(body,info)
  val top=SafaGrid(this,{if(it>=700)2 else 1});ui.add(info,top)
  val typeBox=ui.column();top.addView(typeBox);ui.label(typeBox,"نوع البيع")
  channel=ui.dropdown(listOf("store" to "مبيع المحل","online" to "مبيع أونلاين"),state?.getString("channel") ?: "store"){};ui.add(typeBox,channel.view)
  val currencyBox=ui.column();top.addView(currencyBox);ui.label(currencyBox,"عملة البيع")
  invoiceRate=state?.getString("invoiceRate")?.toBigDecimal() ?: BigDecimal.ONE
  saleUnit=ui.dropdown(listOf("USD" to "دولار","TRY" to "تركي","SYP" to "سوري"),state?.getString("saleUnit") ?: "USD"){value->
   val oldRate=invoiceRate
   invoiceRate=ExchangeRateSettings(this).rate(value,"USD")
   if(::discount.isInitialized){val old=discount.text.toString().toBigDecimalOrNull() ?: BigDecimal.ZERO;discount.setText(money((old*oldRate).divide(invoiceRate,2,RoundingMode.HALF_UP)))}
   if(::cart.isInitialized)refresh()
  };ui.add(currencyBox,saleUnit.view)
  dates=SafaDateTime.fields(ui,info);dates.date.id=6104;dates.time.id=6105
  val client=SafaGrid(this,{if(it>=520)2 else 1});ui.add(info,client)
  fun clientField(label:String,id:Int):EditText{val box=ui.column();ui.label(box,label);return ui.input().apply{this.id=id;ui.add(box,this);client.addView(box)}}
  customer=clientField("اسم العميل",6101);phone=clientField("رقم الهاتف",6102).apply{inputType=android.text.InputType.TYPE_CLASS_PHONE}
  ui.label(info,"مدة الضمان")
  warranty=ui.dropdown(listOf("0" to "بدون ضمان","3" to "3 أشهر","6" to "6 أشهر","12" to "سنة","24" to "سنتان"),state?.getString("warranty") ?: "12"){};ui.add(info,warranty.view)
  ui.step(body,2,"إضافة من المخزون")
  search=ui.input("اسم المنتج أو الباركود");ui.add(body,search);SafaKeyboard.keepContextBelow(search,100);ui.listen(search){lookup(it,false)}
  ui.add(body,ui.button("قراءة الباركود بالكاميرا"){if(!saving)runCatching{startActivityForResult(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),502)}.onFailure{error.text="تعذر فتح الكاميرا"}})
  results=ui.column();results.addOnLayoutChangeListener{_,_,_,_,_,_,_,_,_->if(search.hasFocus())search.post{SafaKeyboard.reveal(search)}};ui.add(body,results);ui.heading(body,"أصناف الفاتورة","");cart=ui.column();ui.add(body,cart)
  ui.label(body,"خصم الفاتورة اليدوي — بعملة البيع");discount=ui.input(numeric=true).apply{id=6103;setText(state?.getString("discount") ?: "0")};ui.add(body,discount);ui.listen(discount){summary()}
  ui.step(body,3,"الدفع والإرسال")
  ui.add(body,ui.text("أضف دفعة لكل عملة وصندوق. اترك الدفعات فارغة للبيع الآجل مع تحديد العميل."))
  ui.add(body,ui.button("＋ إضافة دفعة"){if(!saving)payment()});receipts=ui.column();ui.add(body,receipts)
  changePanel=ui.card();ui.label(changePanel,"ردّ الباقي للعميل")
  val changeGrid=SafaGrid(this,{if(it>=520)2 else 1})
  val cb=ui.column();ui.label(cb,"من أي صندوق؟")
  changeBox=ui.dropdown(listOf("drawer" to "الدرج","external" to "الخارجي","shamcash" to "شام كاش","bank" to "البنك"),state?.getString("changeBox") ?: "drawer"){summary()};ui.add(cb,changeBox.view);changeGrid.addView(cb)
  val cu=ui.column();ui.label(cu,"عملة الباقي")
  changeUnit=ui.dropdown(listOf("USD" to "دولار","TRY" to "تركي","SYP" to "سوري"),state?.getString("changeUnit") ?: "USD"){summary()};ui.add(cu,changeUnit.view);changeGrid.addView(cu)
  ui.add(changePanel,changeGrid);changePreview=ui.text("",18f,bold=true);ui.add(changePanel,changePreview);ui.add(body,changePanel)
  ui.label(body,"ملاحظات");notes=ui.input("تفاصيل الضمان أو اللون أو أي ملاحظة",multiline=true).apply{id=6106};ui.add(body,notes)
  total=ui.text("",19f,bold=true);ui.add(body,total);error=ui.text("",14f,SafaStyle.Palette.danger);ui.add(body,error)
  ui.add(body,ui.button("حفظ الفاتورة",true,48){save()});ui.add(body,ui.button("رجوع"){if(!saving)finish()})
  state?.getString("draft")?.let{restore(JSONObject(it))}
  if(state==null&&intent.getBooleanExtra("resume_shared_draft",false)){
   lines.addAll(SalesDraftSession.lines);payments.addAll(SalesDraftSession.payments)
   customer.setText(SalesDraftSession.customer);phone.setText(SalesDraftSession.phone)
   discount.setText(SalesDraftSession.discount);channel.select(SalesDraftSession.channel)
  }
  refresh();setContentView(host.view)
 }
 private fun draft()=SaleDraft(customer.text.toString().trim(),phone.text.toString().trim(),lines.toList(),payments.toList(),(discount.text.toString().ifBlank{"0"}.toBigDecimal()*invoiceRate).setScale(2,RoundingMode.HALF_UP),channel.value,
  java.time.LocalDate.parse(dates.date.text.toString()).atTime(java.time.LocalTime.parse(dates.time.text.toString())).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
  warranty.value.toInt(),notes.text.toString(),changeBox.value,changeUnit.value,ExchangeRateSettings(this).rate(changeUnit.value,"USD"),saleUnit.value,invoiceRate)
 private fun summary(){if(!::total.isInitialized)return;runCatching{SalesEngine(this).calculate(draft())}.onSuccess{
  changePanel.visibility=if(it.change>BigDecimal.ZERO)android.view.View.VISIBLE else android.view.View.GONE
  changePreview.text=if(it.change>BigDecimal.ZERO)runCatching{val d=draft();val plan=SaleChangePlan.build(it.net,d.payments,d.changeCashbox,d.changeCurrency,d.changeRate);"المبلغ الذي سترده: ${money(plan.change!!.amount)} ${plan.change.currency}"}.getOrElse{e->e.message ?: "راجع عملة الباقي"} else ""
  total.text="الإجمالي: ${display(it.gross)}\nالخصم: ${display(it.discount)}\nالصافي: ${display(it.net)}\nالمقبوض: ${display(it.paid)}\nالمتبقي: ${display(it.due)}\nالباقي للعميل: ${display(it.change)}"
 }.onFailure{total.text=if(lines.isEmpty())"أضف الأصناف" else it.message}}
 private fun display(n:BigDecimal)=money(n.divide(invoiceRate,2,RoundingMode.HALF_UP))+" "+saleUnit.value
 private fun refresh(){cart.removeAllViews();lines.toList().forEachIndexed{i,line->val card=ui.card();ui.add(card,ui.text(line.title,17f,bold=true));ui.add(card,ui.text("${money(line.quantity)} × ${display(line.unitPrice)}"));val actions=SafaGrid(this,{2});actions.addView(ui.button("تعديل الكمية والسعر"){if(!saving)editLine(i,null)});actions.addView(ui.button("حذف"){if(!saving){lines.removeAt(i);refresh()}});ui.add(card,actions);ui.add(cart,card)}
  receipts.removeAllViews();payments.toList().forEachIndexed{i,p->ui.add(receipts,ui.button("${money(p.amount)} ${p.currency} — ${UnifiedCashboxBalance.displayName(p.cashbox)} — حذف"){if(!saving){payments.removeAt(i);refresh()}})};summary()
 }
 private fun lookup(q:String,exact:Boolean){job?.cancel();results.removeAllViews();if(q.isBlank()||saving)return;job=scope.launch{try{if(!exact)delay(180);val found=catalog.find(q,exact);ensureActive();if(exact&&found.size==1)editLine(null,found.single())else found.forEach{p->ui.add(results,ui.button("${p.title} — ${p.price} ${p.currency} — المتاح ${p.available}"){editLine(null,p)})};if(found.isEmpty())ui.add(results,ui.text("لا توجد نتائج"))}catch(e:CancellationException){throw e}catch(e:Exception){error.text=e.message}}}
 private fun editLine(index:Int?,choice:SaleChoice?){if(saving)return;SafaKeyboard.dismiss(search)
  val old=index?.let{lines[it]};val unit=saleUnit.value;val sourceUnit=runCatching{choice?.currency?.let(SaleCurrency::normalize) ?: "USD"}.getOrElse{error.text=it.message;return};val form=ui.column(16)
  fun input(label:String,value:String):EditText{ui.label(form,label);return ui.input(numeric=true).apply{setText(value);ui.add(form,this)}}
  val qty=input("الكمية",old?.quantity?.toPlainString() ?: "1");val price=input("سعر الوحدة — $unit",money((old?.unitPrice ?: (choice!!.price.toBigDecimal()*ExchangeRateSettings(this).rate(sourceUnit,"USD"))).divide(invoiceRate,2,RoundingMode.HALF_UP)))
  val warning=ui.text("",14f,SafaStyle.Palette.danger);ui.add(form,warning)
  val dialog=android.app.AlertDialog.Builder(this).setTitle(old?.title ?: choice!!.title).setView(ScrollView(this).apply{isFillViewport=false;addView(form)}).setNegativeButton("إلغاء",null).setPositiveButton("إضافة / حفظ",null).create()
  dialog.setOnShowListener{SafaKeyboard.fitDialog(dialog);dialog.getButton(-1).setOnClickListener{try{val q=qty.text.toString().toBigDecimal();val p=price.text.toString().toBigDecimal();require(q>BigDecimal.ZERO && p>=BigDecimal.ZERO){"تحقق من الكمية والسعر"}
   if(choice!=null){val reserved=lines.filter{it.productSource==choice.source&&it.productId==choice.productId&&it.variantId==choice.variantId}.fold(BigDecimal.ZERO){a,l->a+l.quantity};require(q+reserved<=choice.available.toBigDecimal()){"الكمية غير متوفرة"};val fx=invoiceRate;lines+=SaleLine(choice.source,choice.productId,choice.variantId,choice.title,q,(p*fx).setScale(2,RoundingMode.HALF_UP),choice.cost.toBigDecimal()*ExchangeRateSettings(this).rate(sourceUnit,"USD"))}
   else lines[index!!]=old!!.copy(quantity=q,unitPrice=(p*invoiceRate).setScale(2,RoundingMode.HALF_UP))
   search.setText("");refresh();dialog.dismiss()
  }catch(e:Exception){warning.text=e.message ?: "راجع القيم"}}};dialog.show()
 }
 private fun payment(){currentFocus?.let{SafaKeyboard.dismiss(it)};val form=ui.column(16);ui.label(form,"المبلغ المقبوض");val amount=ui.input(numeric=true);ui.add(form,amount)
  val unit=ui.dropdown(listOf("USD" to "دولار","TRY" to "تركي","SYP" to "سوري"),"USD"){};ui.add(form,unit.view)
  val box=ui.dropdown(listOf("drawer" to "الدرج","external" to "الخارجي","shamcash" to "شام كاش","bank" to "البنك"),"drawer"){};ui.add(form,box.view)
  val warning=ui.text("",14f,SafaStyle.Palette.danger);ui.add(form,warning)
  val dialog=android.app.AlertDialog.Builder(this).setTitle("إضافة دفعة").setView(ScrollView(this).apply{addView(form)}).setNegativeButton("إلغاء",null).setPositiveButton("إضافة",null).create()
  dialog.setOnShowListener{SafaKeyboard.fitDialog(dialog);dialog.getButton(-1).setOnClickListener{try{val a=amount.text.toString().toBigDecimal();require(a>BigDecimal.ZERO){"أدخل مبلغاً موجباً"};require(box.value!="shamcash"||unit.value!="TRY"){"شام كاش لا يدعم التركية"};val fx=ExchangeRateSettings(this).rate(unit.value,"USD");val credit=(a*fx).setScale(2,RoundingMode.HALF_UP);require(credit>BigDecimal.ZERO){"المبلغ أقل من دقة الفاتورة"};payments+=SalePayment(box.value,unit.value,a,"USD",fx,credit);refresh();dialog.dismiss()}catch(e:Exception){warning.text=e.message}}};dialog.show()
 }
 private fun save(){if(saving)return;val value=runCatching{draft()}.getOrElse{error.text="تحقق من الخصم";return};val totals=runCatching{SalesEngine(this).calculate(value)}.getOrElse{error.text=it.message;return}
  if(totals.change>BigDecimal.ZERO)try{SaleChangePlan.build(totals.net,value.payments,value.changeCashbox,value.changeCurrency,value.changeRate)}catch(e:Exception){error.text=e.message;return}
  if(totals.due>BigDecimal.ZERO&&value.customerName.isBlank()&&value.customerPhone.isBlank()){error.text="حدد العميل للبيع الآجل";return}
  saving=true;error.text="جارٍ الحفظ…";scope.launch{try{val id=withContext(Dispatchers.IO){RoomSalesEngine(applicationContext).save(value,totals,requestId)};if(intent.getBooleanExtra("resume_shared_draft",false))SalesDraftSession.clear();startActivity(Intent(this@FullSaleActivity,SalePrintActivity::class.java).putExtra("sale_id",id));finish()}catch(e:CancellationException){throw e}catch(e:Exception){error.text=e.message ?: "تعذر الحفظ";saving=false}}
 }
 override fun onSaveInstanceState(out:Bundle){out.putString("saleUnit",saleUnit.value);out.putString("invoiceRate",invoiceRate.toPlainString());out.putString("warranty",warranty.value);out.putString("changeBox",changeBox.value);out.putString("changeUnit",changeUnit.value);out.putString("requestId",requestId);out.putString("channel",channel.value);out.putString("discount",discount.text.toString());out.putString("draft",JSONObject().put("lines",JSONArray().apply{lines.forEach{put(JSONObject().put("source",it.productSource).put("id",it.productId).put("variant",it.variantId ?: "").put("title",it.title).put("qty",it.quantity.toPlainString()).put("price",it.unitPrice.toPlainString()).put("cost",it.unitCost.toPlainString()))}}).put("payments",JSONArray().apply{payments.forEach{put(JSONObject().put("box",it.cashbox).put("currency",it.currency).put("amount",it.amount.toPlainString()).put("fx",it.fxRate.toString()).put("credit",it.creditedAmount.toString()))}}).toString());super.onSaveInstanceState(out)}
 private fun restore(j:JSONObject){val a=j.getJSONArray("lines");for(i in 0 until a.length()){val r=a.getJSONObject(i);lines+=SaleLine(r.getString("source"),r.getString("id"),r.getString("variant").ifBlank{null},r.getString("title"),r.getString("qty").toBigDecimal(),r.getString("price").toBigDecimal(),r.getString("cost").toBigDecimal())};val b=j.getJSONArray("payments");for(i in 0 until b.length()){val r=b.getJSONObject(i);payments+=SalePayment(r.getString("box"),r.getString("currency"),r.getString("amount").toBigDecimal(),"USD",r.getString("fx").toBigDecimal(),r.getString("credit").toBigDecimal())}}
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==502&&resultCode==RESULT_OK){val bitmap=data?.extras?.get("data") as? android.graphics.Bitmap ?: return;val scanner=BarcodeScanning.getClient();scanner.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener{rows->if(isFinishing||isDestroyed)return@addOnSuccessListener;rows.firstOrNull()?.rawValue?.let{search.setText(it);lookup(it,true)} ?: run{error.text="لم يُقرأ باركود"}}.addOnFailureListener{if(!isFinishing&&!isDestroyed)error.text="تعذر قراءة الباركود"}.addOnCompleteListener{scanner.close()}}}
 @Deprecated("Legacy back callback") override fun onBackPressed(){if(!saving)super.onBackPressed()}
 override fun onDestroy(){scope.cancel();if(::catalog.isInitialized)catalog.close();super.onDestroy()}
}
