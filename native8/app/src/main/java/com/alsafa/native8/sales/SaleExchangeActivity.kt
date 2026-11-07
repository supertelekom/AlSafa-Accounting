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

/** Replacement and net settlement are previewed before a single atomic write. */
class SaleExchangeActivity:Activity(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private val catalog by lazy{SalesProductCatalog(applicationContext)}
 private var searchJob:Job?=null;private var selected:SaleChoice?=null;private var busy=false
 private var saveDraft:((Bundle)->Unit)?=null
 private var requestId="";private var saleId="";private var lineId=""
 override fun onCreate(state:Bundle?){super.onCreate(state);requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
  saleId=intent.getStringExtra("sale_id").orEmpty();lineId=intent.getStringExtra("line_id").orEmpty();requestId=state?.getString("request") ?: UUID.randomUUID().toString()
  val ui=SafaWidgets(SafaStyle(this));val host=SafaPageHost(ui,"الصفا للساعات");val body=host.body
  ui.heading(body,"استبدال منتج","إرجاع الصنف واختيار البديل وتسوية الفرق")
  val original=ui.text("جارٍ تحميل الصنف…",18f,bold=true);ui.add(body,original)
  fun field(label:String,value:String,numeric:Boolean=false):EditText{ui.label(body,label);return ui.input(numeric=numeric).apply{setText(value);ui.add(body,this)}}
  val quantity=field("الكمية المستبدلة","1",true)
  ui.heading(body,"المنتج البديل","")
  val search=ui.input("بحث بالاسم أو الباركود");ui.add(body,search);val results=ui.column();ui.add(body,results)
  val chosen=ui.text("اختر المنتج البديل");ui.add(body,chosen)
  val rq=field("كمية البديل","1",true);val price=field("سعر الوحدة للبديل — بعملته المعروضة","",true)
  ui.add(body,ui.button("إزالة اختيار البديل"){selected=null;chosen.text="اختر المنتج البديل";price.setText("");search.requestFocus()})
  val difference=ui.text("",18f,bold=true);ui.add(body,difference)
  var refreshQuote:()->Unit={}
  ui.label(body,"صندوق قبض أو رد الفرق")
  val box=ui.dropdown(listOf("drawer" to "الدرج","external" to "الصندوق الخارجي","shamcash" to "شام كاش","bank" to "البنك"),"drawer"){refreshQuote()};ui.add(body,box.view)
  ui.label(body,"عملة قبض أو رد الفرق")
  val unit=ui.dropdown(listOf("USD" to "دولار","TRY" to "ليرة تركية","SYP" to "ليرة سورية"),"USD"){refreshQuote()};ui.add(body,unit.view)
  val amount=field("المقبوض الآن من الفرق — صفر لتركه آجلاً","0",true)
  val reason=field("سبب الاستبدال","");val error=ui.text("",14f,SafaStyle.Palette.danger);ui.add(body,error)
  val save=ui.button("معاينة الاستبدال",true){};save.isEnabled=false;ui.add(body,save);ui.add(body,ui.button("رجوع"){finish()});setContentView(host.view)
  scope.launch{try{val line=withContext(Dispatchers.IO){RoomSalesEngine(applicationContext).lines(saleId).firstOrNull{it.id==lineId}} ?: error("الصنف غير موجود")
   original.text="${line.title}\nالمتاح للاستبدال: ${InvoiceDisplay.amount(line.quantity.toBigDecimal()-line.returned.toBigDecimal())}";save.isEnabled=true
  }catch(e:CancellationException){throw e}catch(e:Exception){error.text=e.message}}
  state?.getString("replacement")?.let{saved->val v=org.json.JSONObject(saved);selected=SaleChoice(v.getString("source"),v.getString("productId"),v.optString("variant").takeIf{it.isNotBlank()},v.getString("title"),v.optString("barcode"),v.getString("available"),v.getString("price"),v.getString("cost"),v.getString("currency"));chosen.text="${selected!!.title} — ${selected!!.currency}"}
  if(state!=null){quantity.setText(state.getString("qty","1"));rq.setText(state.getString("rq","1"));price.setText(state.getString("price",""));amount.setText(state.getString("amount","0"));reason.setText(state.getString("reason",""));box.select(state.getString("box","drawer"));unit.select(state.getString("unit","USD"))}
  saveDraft={out->out.putString("qty",quantity.text.toString());out.putString("rq",rq.text.toString());out.putString("price",price.text.toString());out.putString("amount",amount.text.toString());out.putString("reason",reason.text.toString());out.putString("box",box.value);out.putString("unit",unit.value)
   selected?.let{p->out.putString("replacement",org.json.JSONObject().put("source",p.source).put("productId",p.productId).put("variant",p.variantId.orEmpty()).put("title",p.title).put("barcode",p.barcode).put("available",p.available).put("price",p.price).put("cost",p.cost).put("currency",p.currency).toString())}
  }
  catalog.prepare()
  ui.listen(search){searchJob?.cancel();val query=search.text.toString();searchJob=scope.launch{try{delay(250);val list=catalog.find(query);results.removeAllViews()
   list.forEach{p->ui.add(results,ui.button("${p.title} · ${InvoiceDisplay.amount(p.price)} ${p.currency}",height=48){selected=p;chosen.text="${p.title} — ${p.currency}";price.setText(InvoiceDisplay.amount(p.price));results.removeAllViews();SafaKeyboard.dismiss(search)})}
   if(query.isNotBlank()&&list.isEmpty())ui.add(results,ui.text("لا توجد منتجات مطابقة"))
  }catch(e:CancellationException){throw e}catch(e:Exception){error.text=e.message}}}
  fun number(v:EditText)=v.text.toString().trim().toBigDecimalOrNull() ?: error("أدخل مبلغاً أو كمية صحيحة")
  fun request(collected:BigDecimal):RoomSalesEngine.ExchangeRequest {
   val p=requireNotNull(selected){"اختر المنتج البديل"};val fx=ExchangeRateSettings(this);val productRate=fx.rate(p.currency,"USD")
    val replacement=SaleLine(p.source,p.productId,p.variantId,p.title,number(rq),(number(price)*productRate).setScale(2,RoundingMode.HALF_UP),p.cost.toBigDecimal()*productRate)
    return RoomSalesEngine.ExchangeRequest(requestId,saleId,lineId,number(quantity),replacement,box.value,unit.value,fx.rate(unit.value,"USD"),collected,reason.text.toString().trim())
  }
  var quoteJob:Job?=null
  refreshQuote={
   quoteJob?.cancel()
   difference.text=""
   if(selected!=null)quoteJob=scope.launch{
    try{
     val q=request(BigDecimal.ZERO)
     val quote=withContext(Dispatchers.IO){RoomSalesEngine(applicationContext).quoteExchange(q)}
     fun cash(value:BigDecimal)=InvoiceDisplay.amount(value.divide(q.rate,2,RoundingMode.HALF_UP))+" "+q.currency
     difference.text=when{
      quote.settlement.refund.signum()>0 -> "يُردّ للعميل: "+cash(quote.settlement.refund)
      quote.settlement.due.signum()>0 -> "المطلوب من العميل قبل القبض: "+cash(quote.settlement.due)
      else -> "لا يوجد مبلغ للقبض أو الرد"
     }
    }catch(e:CancellationException){throw e}catch(e:Exception){difference.text=e.message}
   }
  }
  listOf(quantity,rq,price).forEach{ui.listen(it){refreshQuote()}}
  refreshQuote()
  save.setOnClickListener{if(busy)return@setOnClickListener
   try{val q=request(number(amount))
    busy=true;save.isEnabled=false;SafaKeyboard.dismiss(amount)
    scope.launch{try{val engine=RoomSalesEngine(applicationContext);val preview=withContext(Dispatchers.IO){engine.previewExchange(q)};val s=preview.settlement
     fun cash(n:BigDecimal)=InvoiceDisplay.amount(n.divide(q.rate,2,RoundingMode.HALF_UP))+" "+q.currency
     var accepted=false
     ui.confirm("تأكيد الاستبدال","قيمة المرتجع: ${InvoiceDisplay.amount(preview.returned)} USD\nقيمة البديل: ${InvoiceDisplay.amount(preview.replacement)} USD\nالمردود للعميل: ${cash(s.refund)}\nالمقبوض الآن: ${InvoiceDisplay.amount(q.collect)} ${q.currency}\nالمتبقي بعد الاستبدال: ${cash(s.due)}\nالصندوق: ${UnifiedCashboxBalance.displayName(q.cashbox)}"){
      accepted=true;busy=true;save.isEnabled=false
      scope.launch{try{withContext(Dispatchers.IO){engine.exchange(q,preview.fingerprint)};setResult(RESULT_OK);startActivity(Intent(this@SaleExchangeActivity,SalePrintActivity::class.java).putExtra("return_key","exchange:$requestId"));finish()}
       catch(e:CancellationException){throw e}catch(e:Exception){error.text=e.message;busy=false;save.isEnabled=true}}
     }.also{dialog->dialog.setOnDismissListener{if(!accepted){busy=false;save.isEnabled=true}};dialog.show()}
    }catch(e:CancellationException){throw e}catch(e:Exception){error.text=e.message;busy=false;save.isEnabled=true}}
   }catch(e:Exception){error.text=e.message}
  }
 }
 override fun onSaveInstanceState(out:Bundle){out.putString("request",requestId);saveDraft?.invoke(out);super.onSaveInstanceState(out)}
 override fun onDestroy(){catalog.close();scope.cancel();super.onDestroy()}
}
