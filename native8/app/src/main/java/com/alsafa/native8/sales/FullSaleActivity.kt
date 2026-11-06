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
 private lateinit var channel:SafaWidgets.Choice
 private val lines=mutableListOf<SaleLine>();private val payments=mutableListOf<SalePayment>()
 private var requestId=UUID.randomUUID().toString();private var saving=false;private var job:Job?=null
 private fun money(n:BigDecimal)=InvoiceDisplay.amount(n)
 override fun onCreate(state:Bundle?){super.onCreate(state);requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
  ui=SafaWidgets(SafaStyle(this));catalog=SalesProductCatalog(applicationContext);catalog.prepare()
  requestId=state?.getString("requestId") ?: requestId
  val host=SafaPageHost(ui,"الصفا للساعات");val body=host.body;ui.heading(body,"فاتورة مبيع","الأصناف والأسعار والقبض")
  channel=ui.choice(listOf("store" to "مبيع المحل","online" to "مبيع أونلاين"),state?.getString("channel") ?: "store"){};ui.add(body,channel.view)
  val client=SafaGrid(this,{if(it>=520)2 else 1});ui.add(body,client)
  fun clientField(label:String,id:Int):EditText{val box=ui.column();ui.label(box,label);return ui.input().apply{this.id=id;ui.add(box,this);client.addView(box)}}
  customer=clientField("اسم العميل",6101);phone=clientField("رقم الهاتف",6102).apply{inputType=android.text.InputType.TYPE_CLASS_PHONE}
  ui.step(body,1,"الأصناف")
  search=ui.input("اسم المنتج أو الباركود");ui.add(body,search);ui.listen(search){lookup(it,false)}
  ui.add(body,ui.button("قراءة الباركود بالكاميرا"){if(!saving)runCatching{startActivityForResult(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),502)}.onFailure{error.text="تعذر فتح الكاميرا"}})
  results=ui.column();ui.add(body,results);cart=ui.column();ui.add(body,cart)
  ui.label(body,"الخصم الإضافي — USD");discount=ui.input(numeric=true).apply{id=6103;setText(state?.getString("discount") ?: "0")};ui.add(body,discount);ui.listen(discount){summary()}
  ui.step(body,2,"القبض والدفعات")
  ui.add(body,ui.text("أضف دفعة لكل عملة وصندوق. اترك الدفعات فارغة للبيع الآجل مع تحديد العميل."))
  ui.add(body,ui.button("＋ إضافة دفعة"){if(!saving)payment()});receipts=ui.column();ui.add(body,receipts)
  total=ui.text("",19f,bold=true);ui.add(body,total);error=ui.text("",14f,SafaStyle.Palette.danger);ui.add(body,error)
  ui.add(body,ui.complete("حفظ الفاتورة"){save()});ui.add(body,ui.button("رجوع"){if(!saving)finish()})
  state?.getString("draft")?.let{restore(JSONObject(it))}
  if(state==null&&intent.getBooleanExtra("resume_shared_draft",false)){
   lines.addAll(SalesDraftSession.lines);payments.addAll(SalesDraftSession.payments)
   customer.setText(SalesDraftSession.customer);phone.setText(SalesDraftSession.phone)
   discount.setText(SalesDraftSession.discount);channel.select(SalesDraftSession.channel)
  }
  refresh();setContentView(host.view)
 }
 private fun draft()=SaleDraft(customer.text.toString().trim(),phone.text.toString().trim(),lines.toList(),payments.toList(),discount.text.toString().ifBlank{"0"}.toBigDecimal(),channel.value)
 private fun summary(){if(!::total.isInitialized)return;runCatching{SalesEngine(this).calculate(draft())}.onSuccess{
  total.text="الإجمالي: ${money(it.gross)} USD\nالخصم: ${money(it.discount)} USD\nالصافي: ${money(it.net)} USD\nالمقبوض: ${money(it.paid)} USD\nالمتبقي: ${money(it.due)} USD\nالباقي للعميل: ${money(it.change)} USD"
 }.onFailure{total.text=if(lines.isEmpty())"أضف الأصناف" else it.message}}
 private fun refresh(){cart.removeAllViews();lines.toList().forEachIndexed{i,line->val card=ui.card();ui.add(card,ui.text(line.title,17f,bold=true));ui.add(card,ui.text("${money(line.quantity)} × ${money(line.unitPrice)} USD"));val actions=SafaGrid(this,{2});actions.addView(ui.button("تعديل الكمية والسعر"){if(!saving)editLine(i,null)});actions.addView(ui.button("حذف"){if(!saving){lines.removeAt(i);refresh()}});ui.add(card,actions);ui.add(cart,card)}
  receipts.removeAllViews();payments.toList().forEachIndexed{i,p->ui.add(receipts,ui.button("${money(p.amount)} ${p.currency} — ${UnifiedCashboxBalance.displayName(p.cashbox)} — حذف"){if(!saving){payments.removeAt(i);refresh()}})};summary()
 }
 private fun lookup(q:String,exact:Boolean){job?.cancel();results.removeAllViews();if(q.isBlank()||saving)return;job=scope.launch{try{if(!exact)delay(180);val found=catalog.find(q,exact);ensureActive();if(exact&&found.size==1)editLine(null,found.single())else found.forEach{p->ui.add(results,ui.button("${p.title} — ${p.price} ${p.currency} — المتاح ${p.available}"){editLine(null,p)})};if(found.isEmpty())ui.add(results,ui.text("لا توجد نتائج"))}catch(e:CancellationException){throw e}catch(e:Exception){error.text=e.message}}}
 private fun editLine(index:Int?,choice:SaleChoice?){if(saving)return
  val old=index?.let{lines[it]};val unit=choice?.currency ?: "USD";val form=ui.column(16)
  fun input(label:String,value:String):EditText{ui.label(form,label);return ui.input(numeric=true).apply{setText(value);ui.add(form,this)}}
  val qty=input("الكمية",old?.quantity?.toPlainString() ?: "1");val price=input("سعر الوحدة — $unit",old?.unitPrice?.toPlainString() ?: choice!!.price)
  val warning=ui.text("",14f,SafaStyle.Palette.danger);ui.add(form,warning)
  val dialog=android.app.AlertDialog.Builder(this).setTitle(old?.title ?: choice!!.title).setView(form).setNegativeButton("إلغاء",null).setPositiveButton("إضافة / حفظ",null).create()
  dialog.setOnShowListener{dialog.getButton(-1).setOnClickListener{try{val q=qty.text.toString().toBigDecimal();val p=price.text.toString().toBigDecimal();require(q>BigDecimal.ZERO && p>=BigDecimal.ZERO){"تحقق من الكمية والسعر"}
   if(choice!=null){val reserved=lines.filter{it.productSource==choice.source&&it.productId==choice.productId&&it.variantId==choice.variantId}.fold(BigDecimal.ZERO){a,l->a+l.quantity};require(q+reserved<=choice.available.toBigDecimal()){"الكمية غير متوفرة"};val fx=ExchangeRateSettings(this).rate(unit,"USD");lines+=SaleLine(choice.source,choice.productId,choice.variantId,choice.title,q,p*fx,choice.cost.toBigDecimal()*fx)}
   else lines[index!!]=old!!.copy(quantity=q,unitPrice=p)
   search.setText("");refresh();dialog.dismiss()
  }catch(e:Exception){warning.text=e.message ?: "راجع القيم"}}};dialog.show()
 }
 private fun payment(){val form=ui.column(16);ui.label(form,"المبلغ المقبوض");val amount=ui.input(numeric=true);ui.add(form,amount)
  val unit=ui.choice(listOf("USD" to "دولار","TRY" to "تركي","SYP" to "سوري"),"USD"){};ui.add(form,unit.view)
  val box=ui.choice(listOf("drawer" to "الدرج","external" to "الخارجي","shamcash" to "شام كاش","bank" to "البنك"),"drawer",true){};ui.add(form,box.view)
  val warning=ui.text("",14f,SafaStyle.Palette.danger);ui.add(form,warning)
  val dialog=android.app.AlertDialog.Builder(this).setTitle("إضافة دفعة").setView(ScrollView(this).apply{addView(form)}).setNegativeButton("إلغاء",null).setPositiveButton("إضافة",null).create()
  dialog.setOnShowListener{dialog.getButton(-1).setOnClickListener{try{val a=amount.text.toString().toBigDecimal();require(a>BigDecimal.ZERO){"أدخل مبلغاً موجباً"};require(box.value!="shamcash"||unit.value!="TRY"){"شام كاش لا يدعم التركية"};val fx=ExchangeRateSettings(this).rate(unit.value,"USD");val credit=(a*fx).setScale(2,RoundingMode.HALF_UP);require(credit>BigDecimal.ZERO){"المبلغ أقل من دقة الفاتورة"};payments+=SalePayment(box.value,unit.value,a,"USD",fx,credit);refresh();dialog.dismiss()}catch(e:Exception){warning.text=e.message}}};dialog.show()
 }
 private fun save(){if(saving)return;val value=runCatching{draft()}.getOrElse{error.text="تحقق من الخصم";return};val totals=runCatching{SalesEngine(this).calculate(value)}.getOrElse{error.text=it.message;return}
  if(totals.change>BigDecimal.ZERO){error.text="الباقي للعميل ${money(totals.change)} USD. صرف الباقي غير متاح بعد؛ عدّل الدفعة إلى المبلغ المحتفظ به فعلياً قبل الحفظ.";return}
  if(totals.due>BigDecimal.ZERO&&value.customerName.isBlank()&&value.customerPhone.isBlank()){error.text="حدد العميل للبيع الآجل";return}
  saving=true;error.text="جارٍ الحفظ…";scope.launch{try{val id=withContext(Dispatchers.IO){RoomSalesEngine(applicationContext).save(value,totals,requestId)};if(intent.getBooleanExtra("resume_shared_draft",false))SalesDraftSession.clear();startActivity(Intent(this@FullSaleActivity,SalePrintActivity::class.java).putExtra("sale_id",id));finish()}catch(e:CancellationException){throw e}catch(e:Exception){error.text=e.message ?: "تعذر الحفظ";saving=false}}
 }
 override fun onSaveInstanceState(out:Bundle){out.putString("requestId",requestId);out.putString("channel",channel.value);out.putString("discount",discount.text.toString());out.putString("draft",JSONObject().put("lines",JSONArray().apply{lines.forEach{put(JSONObject().put("source",it.productSource).put("id",it.productId).put("variant",it.variantId ?: "").put("title",it.title).put("qty",it.quantity.toPlainString()).put("price",it.unitPrice.toPlainString()).put("cost",it.unitCost.toPlainString()))}}).put("payments",JSONArray().apply{payments.forEach{put(JSONObject().put("box",it.cashbox).put("currency",it.currency).put("amount",it.amount.toPlainString()).put("fx",it.fxRate.toString()).put("credit",it.creditedAmount.toString()))}}).toString());super.onSaveInstanceState(out)}
 private fun restore(j:JSONObject){val a=j.getJSONArray("lines");for(i in 0 until a.length()){val r=a.getJSONObject(i);lines+=SaleLine(r.getString("source"),r.getString("id"),r.getString("variant").ifBlank{null},r.getString("title"),r.getString("qty").toBigDecimal(),r.getString("price").toBigDecimal(),r.getString("cost").toBigDecimal())};val b=j.getJSONArray("payments");for(i in 0 until b.length()){val r=b.getJSONObject(i);payments+=SalePayment(r.getString("box"),r.getString("currency"),r.getString("amount").toBigDecimal(),"USD",r.getString("fx").toBigDecimal(),r.getString("credit").toBigDecimal())}}
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==502&&resultCode==RESULT_OK){val bitmap=data?.extras?.get("data") as? android.graphics.Bitmap ?: return;val scanner=BarcodeScanning.getClient();scanner.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener{rows->if(isFinishing||isDestroyed)return@addOnSuccessListener;rows.firstOrNull()?.rawValue?.let{search.setText(it);lookup(it,true)} ?: run{error.text="لم يُقرأ باركود"}}.addOnFailureListener{if(!isFinishing&&!isDestroyed)error.text="تعذر قراءة الباركود"}.addOnCompleteListener{scanner.close()}}}
 @Deprecated("Legacy back callback") override fun onBackPressed(){if(!saving)super.onBackPressed()}
 override fun onDestroy(){scope.cancel();if(::catalog.isInitialized)catalog.close();super.onDestroy()}
}
