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
import java.time.ZoneId

class SalesArchiveActivity:Activity(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main);private var job:Job?=null
 private lateinit var search:EditText;private lateinit var from:EditText;private lateinit var to:EditText
 private lateinit var notice:TextView;private lateinit var adapter:BaseAdapter;private var rows=listOf<SaleRecord>()
 override fun onCreate(state:Bundle?){super.onCreate(state);requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
  val ui=SafaWidgets(SafaStyle(this));val root=ui.column(16).apply{setBackgroundColor(SafaStyle.Palette.cream)};ui.heading(root,"فواتير المبيع","الفواتير القديمة والجديدة")
  val actions=SafaGrid(this,{2});actions.addView(ui.button("＋ فاتورة مبيع",true){startActivity(Intent(this,FullSaleActivity::class.java))});actions.addView(ui.button("سجل المرتجعات"){startActivity(Intent(this,SalesReturnsHistoryActivity::class.java))});ui.add(root,actions)
  search=ui.input("الاسم أو الهاتف أو رقم الفاتورة").apply{setText(state?.getString("query"))};ui.add(root,search)
  val dates=SafaGrid(this,{2});from=ui.input("من تاريخ").apply{setText(state?.getString("from"))};to=ui.input("إلى تاريخ").apply{setText(state?.getString("to"))};dates.addView(from);dates.addView(to);ui.add(root,dates)
  if(SafaDateTime.fullMode(this)){SafaDateTime.bindDate(from);SafaDateTime.bindDate(to);SafaDateTime.presets(ui,root,from,to)}
  ui.add(root,ui.button("كل التواريخ"){from.setText("");to.setText("")})
  notice=ui.text("");ui.add(root,notice)
  adapter=object:BaseAdapter(){override fun getCount()=rows.size;override fun getItem(i:Int)=rows[i];override fun getItemId(i:Int)=i.toLong();override fun getView(i:Int,old:View?,parent:ViewGroup):View {val r=rows[i];return ui.button("#${r.number} — ${r.customer.ifBlank{"عميل نقدي"}}\n${InvoiceDisplay.amount(r.total)} — ${r.status}",height=74){startActivity(Intent(this@SalesArchiveActivity,SaleDetailsActivity::class.java).putExtra("sale_id",r.id))}}}
  root.addView(ListView(this).apply{adapter=this@SalesArchiveActivity.adapter;dividerHeight=ui.style.dp(8)},LinearLayout.LayoutParams(-1,0,1f));ui.add(root,ui.button("رجوع"){finish()});setContentView(root)
  listOf(search,from,to).forEach{ui.listen(it){reload()}}
 }
 private fun reload(){if(!::search.isInitialized)return;job?.cancel();val q=search.text.toString();val a=from.text.toString();val b=to.text.toString();job=scope.launch{try{delay(200);require(a.isBlank()||b.isBlank()||a<=b){"تاريخ البداية يجب أن يسبق النهاية"};val zone=ZoneId.systemDefault();val start=a.takeIf{it.isNotBlank()}?.let{LocalDate.parse(it).atStartOfDay(zone).toInstant().toEpochMilli()};val end=b.takeIf{it.isNotBlank()}?.let{LocalDate.parse(it).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()-1};rows=withContext(Dispatchers.IO){SalesEngine(applicationContext).archive(q,start,end)};adapter.notifyDataSetChanged();notice.text="${rows.size} فاتورة"}catch(e:CancellationException){throw e}catch(e:Exception){notice.text=e.message}}}
 override fun onResume(){super.onResume();reload()}
 override fun onSaveInstanceState(out:Bundle){out.putString("query",search.text.toString());out.putString("from",from.text.toString());out.putString("to",to.text.toString());super.onSaveInstanceState(out)}
 override fun onDestroy(){scope.cancel();super.onDestroy()}
}
