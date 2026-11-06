package com.alsafa.native8.maintenance
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.alsafa.native8.stylekit.*
import com.alsafa.native8.sales.*
import kotlinx.coroutines.*
import org.json.JSONObject

class MaintenancePartsActivity:Activity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private val repo by lazy{MaintenancePartsRepository(AppDatabase.get(applicationContext))}
 private lateinit var ui:SafaWidgets
 private var query="";private var job:Job?=null;private var editing=false
 override fun onCreate(state:Bundle?){super.onCreate(state);requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);ui=SafaWidgets(SafaStyle(this));window.statusBarColor=SafaStyle.Palette.cream;list()}
 override fun onDestroy(){scope.cancel();super.onDestroy()}
 @Deprecated("Legacy back callback") override fun onBackPressed(){if(editing)list() else super.onBackPressed()}
 private fun list() {
  editing=false;job?.cancel()
  val root=ui.column(16).apply{setBackgroundColor(SafaStyle.Palette.cream)}
  ui.heading(root,"قطع الصيانة","المستودع والأسعار وحد التنبيه")
  ui.add(root,ui.button("＋ إضافة قطعة",true){edit(null)})
  val search=ui.input("الاسم أو الباركود").apply{setText(query)};ui.add(root,search)
  val notice=ui.text("");ui.add(root,notice)
  var rows=listOf<MaintenancePartEntity>()
  val adapter=object:BaseAdapter(){override fun getCount()=rows.size;override fun getItem(p:Int)=rows[p];override fun getItemId(p:Int)=p.toLong()
   override fun getView(p:Int,old:View?,parent:ViewGroup):View {val r=rows[p];val low=r.stock.toBigDecimalOrNull()?.let{it <= (JSONObject(r.original_json).optString("minStock","2").toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO)} ?: false;return ui.button("${r.name} · ${r.category}\nالمتاح ${InvoiceDisplay.amount(r.stock)} — السعر ${InvoiceDisplay.amount(r.sale_price)} ${r.currency}${if(low) " · مخزون منخفض" else ""}",height=78){edit(r)}}
  }
  val list=ListView(this).apply{this.adapter=adapter;dividerHeight=ui.style.dp(8)};root.addView(list,LinearLayout.LayoutParams(-1,0,1f))
  ui.add(root,ui.button("رجوع"){finish()});setContentView(root)
  fun load(){job?.cancel();val q=query;job=scope.launch{try{delay(250);rows=withContext(Dispatchers.IO){repo.list(q)};adapter.notifyDataSetChanged();notice.text="${rows.size} قطعة"}catch(e:CancellationException){throw e}catch(e:Exception){notice.text=e.message ?: "تعذر القراءة"}}}
  ui.listen(search){query=it;load()};load()
 }
 private fun edit(part:MaintenancePartEntity?) {
  editing=true;job?.cancel();val raw=part?.original_json?.let{JSONObject(it)} ?: JSONObject()
  val host=SafaPageHost(ui,"الصفا للساعات");val body=host.body;ui.heading(body,if(part==null)"إضافة قطعة صيانة" else "تعديل قطعة الصيانة","")
  fun field(label:String,value:String,numeric:Boolean=false):EditText{ui.label(body,label);return ui.input(numeric=numeric).apply{setText(value);ui.add(body,this)}}
  val name=field("اسم القطعة *",part?.name.orEmpty())
  val category=field("النوع",part?.category ?: "بطاريات")
  val barcode=field("الباركود",part?.barcode.orEmpty())
  val stock=field("الكمية",part?.stock ?: "1",true)
  val min=field("حد تنبيه نقص المخزون",raw.optString("minStock","2"),true)
  ui.label(body,"العملة");val unit=ui.choice(listOf("USD" to "دولار","TRY" to "تركي","SYP" to "سوري"),part?.currency ?: "USD"){};ui.add(body,unit.view)
  val cost=field("كلفة القطعة",part?.cost ?: "0",true)
  val price=field("سعر البيع",part?.sale_price ?: "0",true)
  val notes=field("ملاحظات",raw.optString("notes"))
  val error=ui.text("",14f,SafaStyle.Palette.danger);ui.add(body,error)
  val save=ui.button("حفظ",true){};ui.add(body,save)
  save.setOnClickListener {
   val values=listOf(name,category,barcode,stock,min,cost,price,notes).map{it.text.toString()};val currency=unit.value;save.isEnabled=false
   scope.launch{try{withContext(Dispatchers.IO){repo.save(part?.id,values[0],values[1],values[2],values[3],values[4],currency,values[5],values[6],values[7])};list()}
    catch(e:CancellationException){throw e}catch(e:Exception){error.text=e.message ?: "تعذر الحفظ"}finally{save.isEnabled=true}}
  }
  if(part?.source_sha256=="native8")ui.add(body,ui.button("حذف القطعة"){ui.confirm("حذف القطعة","حذف ${part.name}؟ القطعة المرتبطة بفواتير تبقى محمية."){
   scope.launch{try{withContext(Dispatchers.IO){repo.delete(part.id)};list()}catch(e:Exception){error.text=e.message ?: "تعذر الحذف"}}
  }.show()})
  ui.add(body,ui.button("إلغاء"){list()});setContentView(host.view)
 }
}
