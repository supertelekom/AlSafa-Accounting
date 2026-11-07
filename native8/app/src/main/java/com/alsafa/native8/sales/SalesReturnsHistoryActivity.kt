package com.alsafa.native8.sales

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.alsafa.native8.stylekit.*
import kotlinx.coroutines.*

/** Original return/exchange document list; read-only and linked to original invoices. */
class SalesReturnsHistoryActivity:Activity(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private lateinit var ui:SafaWidgets;private lateinit var notice:TextView;private lateinit var query:EditText
 private lateinit var kind:SafaWidgets.Dropdown;private lateinit var adapter:BaseAdapter
 private var all=listOf<ReturnDocument>();private var shown=listOf<ReturnDocument>();private var task:Job?=null
 override fun onCreate(state:Bundle?){super.onCreate(state);requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
  ui=SafaWidgets(SafaStyle(this));val root=ui.column(16).apply{setBackgroundColor(SafaStyle.Palette.cream)}
  ui.heading(root,"فواتير المرتجع والاستبدال","كل مرتجع محفوظ كمستند مستقل ومرتبط بفاتورته الأصلية")
  query=ui.input("رقم الفاتورة أو الصنف أو السبب").apply{setText(state?.getString("query"))};ui.add(root,query)
  kind=ui.dropdown(listOf("all" to "الكل","return" to "المرتجعات","exchange" to "الاستبدال"),state?.getString("kind") ?: "all"){filter()};ui.add(root,kind.view)
  notice=ui.text("جارٍ تحميل السجل…");ui.add(root,notice)
  adapter=object:BaseAdapter(){override fun getCount()=shown.size;override fun getItem(i:Int)=shown[i];override fun getItemId(i:Int)=i.toLong()
   override fun getView(i:Int,old:View?,parent:ViewGroup):View {
    val r=shown[i];val card=ui.card(12)
    ui.add(card,ui.text("${r.title} · الأصلية #${r.invoice}",17f,bold=true),4)
    ui.add(card,ui.text(r.item,14f),4);ui.add(card,ui.text(r.date,12f,SafaStyle.Palette.muted),6)
    r.rows.filter{it.first in setOf("قيمة المرتجع","المردود الفعلي","المقبوض من الفرق")}.forEach{ui.add(card,ui.text("${it.first}: ${it.second}",14f),3)}
    val actions=SafaGrid(this@SalesReturnsHistoryActivity,{if(it>=520)3 else 2},8)
    actions.addView(ui.button("التفاصيل"){details(r)})
    actions.addView(ui.button("PDF"){startActivity(Intent(this@SalesReturnsHistoryActivity,SalePrintActivity::class.java).putExtra("return_key",r.key))})
    if(r.originalAvailable)actions.addView(ui.button("الفاتورة الأصلية"){startActivity(Intent(this@SalesReturnsHistoryActivity,if(r.repair)com.alsafa.native8.maintenance.MaintenanceActivity::class.java else SaleDetailsActivity::class.java).putExtra(if(r.repair)"repair_id" else "sale_id",r.referenceId))})
    ui.add(card,actions,0);return card
   }
  }
  root.addView(ListView(this).apply{adapter=this@SalesReturnsHistoryActivity.adapter;dividerHeight=ui.style.dp(10)},LinearLayout.LayoutParams(-1,0,1f))
  ui.add(root,ui.button("رجوع"){finish()});root.setOnApplyWindowInsetsListener{v,i ->
   if(android.os.Build.VERSION.SDK_INT>=30){val b=i.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime());val p=ui.style.dp(16);v.setPadding(p+b.left,p+b.top,p+b.right,p+b.bottom)}
   i
  };setContentView(root);root.requestApplyInsets();ui.listen(query){filter()}
 }
 private fun filter(){if(!::adapter.isInitialized)return;val q=query.text.toString().trim()
  shown=all.filter{(kind.value=="all" || (it.title=="فاتورة استبدال")== (kind.value=="exchange")) && (q.isBlank()||it.invoice.contains(q)||it.item.contains(q,true)||it.rows.any{r->r.second.contains(q,true)})}
  adapter.notifyDataSetChanged();notice.text=if(shown.isEmpty())"لا توجد فواتير مرتجع أو استبدال مطابقة" else "${shown.size} مستند"
 }
 private fun details(r:ReturnDocument){val content=ui.column(16);r.rows.forEach{ui.label(content,it.first);ui.add(content,ui.text(it.second,16f))}
  android.app.AlertDialog.Builder(this).setTitle(r.title).setView(ScrollView(this).apply{addView(content)}).setPositiveButton("إغلاق",null).show()
 }
 override fun onResume(){super.onResume();task?.cancel();task=scope.launch{try{all=ReturnHistoryRepository(applicationContext).load();filter()}catch(e:CancellationException){throw e}catch(e:Exception){notice.text=e.message ?: "تعذر عرض السجل"}}}
 override fun onSaveInstanceState(out:Bundle){out.putString("query",query.text.toString());out.putString("kind",kind.value);super.onSaveInstanceState(out)}
 override fun onDestroy(){scope.cancel();super.onDestroy()}
}
