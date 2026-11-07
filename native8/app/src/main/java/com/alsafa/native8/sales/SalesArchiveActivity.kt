package com.alsafa.native8.sales
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.alsafa.native8.stylekit.*
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId

class SalesArchiveActivity:Activity(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main);private var job:Job?=null
 private lateinit var search:EditText;private lateinit var from:EditText;private lateinit var to:EditText
 private lateinit var notice:TextView;private lateinit var adapter:BaseAdapter;private lateinit var more:Button
 private var rows=listOf<SaleRecord>();private var offset=0;private var moreAvailable=false
 override fun onCreate(state:Bundle?){super.onCreate(state);requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
  val ui=SafaWidgets(SafaStyle(this));val root=ui.column(16).apply{setBackgroundColor(SafaStyle.Palette.cream)};ui.heading(root,"المبيعات والفواتير","الفواتير القديمة والجديدة")
  val actions=SafaGrid(this,{2});actions.addView(ui.button("＋ فاتورة",true){startActivity(Intent(this,FullSaleActivity::class.java))});actions.addView(ui.button("المرتجعات والاستبدال"){startActivity(Intent(this,SalesReturnsHistoryActivity::class.java))});ui.add(root,actions)
  search=ui.input("الاسم أو الهاتف أو رقم الفاتورة أو الصنف").apply{setText(state?.getString("query"))};ui.add(root,search)
  val dates=SafaGrid(this,{2});from=ui.input("من تاريخ").apply{setText(state?.getString("from"))};to=ui.input("إلى تاريخ").apply{setText(state?.getString("to"))};dates.addView(from);dates.addView(to);ui.add(root,dates)
  if(SafaDateTime.fullMode(this)){SafaDateTime.bindDate(from);SafaDateTime.bindDate(to)}
  val controls=SafaGrid(this,{2});controls.addView(ui.button("اليوم"){val today=LocalDate.now().toString();from.setText(today);to.setText(today)});controls.addView(ui.button("كل التواريخ"){from.setText("");to.setText("")});ui.add(root,controls)
  notice=ui.text("");ui.add(root,notice)
  fun open(r:SaleRecord,type:Class<*>)=startActivity(Intent(this,type).putExtra("sale_id",r.id))
  adapter=object:BaseAdapter(){override fun getCount()=rows.size;override fun getItem(i:Int)=rows[i];override fun getItemId(i:Int)=i.toLong()
   override fun getView(i:Int,old:View?,parent:ViewGroup):View{val r=rows[i];val card=ui.card(12)
    ui.add(card,ui.text("فاتورة #${r.number} · ${r.customer.ifBlank{"عميل نقدي"}}",18f,bold=true),4)
    val date=Instant.ofEpochMilli(r.date).atZone(ZoneId.systemDefault()).toLocalDateTime().withSecond(0).withNano(0).toString().replace('T',' ')
    ui.add(card,ui.text("${if(r.channel=="online")"أونلاين" else "المحل"} · $date · ${r.status}",12f,SafaStyle.Palette.muted),4)
    ui.add(card,ui.text(r.items,14f),4)
    ui.add(card,ui.text("الإجمالي: ${InvoiceDisplay.amount(r.total)} ${r.currency} · المتبقي: ${InvoiceDisplay.amount(r.remaining)} ${r.currency}",16f,bold=true),8)
    val buttons=SafaGrid(this@SalesArchiveActivity,{if(it>=640)3 else 2},8)
    buttons.addView(ui.button("التفاصيل",height=48){open(r,SaleDetailsActivity::class.java)})
    buttons.addView(ui.button("تعديل البيانات",height=48){if(r.historical)startActivity(Intent(this@SalesArchiveActivity,InvoiceMetadataActivity::class.java).putExtra("kind","sale").putExtra("invoice_id",r.id)) else open(r,SaleDetailsActivity::class.java)})
    buttons.addView(ui.button("فاتورة / PDF / طباعة",height=48){open(r,SalePrintActivity::class.java)})
    if(r.status!="ملغاة" && r.remaining.toBigDecimalOrNull()?.signum()==1)buttons.addView(ui.button(if(r.channel=="online")"تحصيل الأونلاين" else "تكملة الدفع",height=48){open(r,SalePaymentsActivity::class.java)})
    ui.add(card,buttons,0);return card
   }}
  root.addView(ListView(this).apply{adapter=this@SalesArchiveActivity.adapter;dividerHeight=ui.style.dp(10)},LinearLayout.LayoutParams(-1,0,1f))
  val footer=SafaGrid(this,{2});more=ui.button("تحميل المزيد",height=48){reload(true)};footer.addView(more);footer.addView(ui.button("رجوع",height=48){finish()});ui.add(root,footer)
  root.setOnApplyWindowInsetsListener{v,i->if(android.os.Build.VERSION.SDK_INT>=30){val b=i.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime());val p=ui.style.dp(16);v.setPadding(p+b.left,p+b.top,p+b.right,p+b.bottom)};i}
  setContentView(root);root.requestApplyInsets();listOf(search,from,to).forEach{ui.listen(it){reload()}}
 }
 private fun reload(append:Boolean=false){if(!::search.isInitialized)return;job?.cancel();val q=search.text.toString();val a=from.text.toString();val b=to.text.toString();if(!append){offset=0;rows=emptyList();adapter.notifyDataSetChanged()};more.isEnabled=false
  job=scope.launch{try{delay(200);require(a.isBlank()||b.isBlank()||a<=b){"تاريخ البداية يجب أن يسبق النهاية"};val zone=ZoneId.systemDefault();val start=a.takeIf{it.isNotBlank()}?.let{LocalDate.parse(it).atStartOfDay(zone).toInstant().toEpochMilli()};val end=b.takeIf{it.isNotBlank()}?.let{LocalDate.parse(it).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()-1}
   val page=withContext(Dispatchers.IO){RoomSalesEngine(applicationContext).archive(q,start,end,offset)};offset+=page.size;rows=(rows+page).distinctBy{it.id};moreAvailable=page.size==100;adapter.notifyDataSetChanged();notice.text=if(rows.isEmpty())"لا توجد فواتير مطابقة" else "${rows.size} فاتورة${if(moreAvailable)" — توجد فواتير أخرى" else ""}";more.isEnabled=moreAvailable
  }catch(e:CancellationException){throw e}catch(e:Exception){notice.text=e.message;more.isEnabled=append}}}
 override fun onResume(){super.onResume();reload()}
 override fun onSaveInstanceState(out:Bundle){out.putString("query",search.text.toString());out.putString("from",from.text.toString());out.putString("to",to.text.toString());super.onSaveInstanceState(out)}
 override fun onDestroy(){scope.cancel();super.onDestroy()}
}
