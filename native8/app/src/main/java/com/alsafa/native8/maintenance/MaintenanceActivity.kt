package com.alsafa.native8.maintenance

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.alsafa.native8.stylekit.*
import com.alsafa.native8.sales.*
import kotlinx.coroutines.*
import org.json.JSONObject

/** Styled paged register and details. List queries deliberately exclude photographs/JSON. */
class MaintenanceActivity:Activity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private val db by lazy{AppDatabase.get(applicationContext)}
 private lateinit var ui:SafaWidgets
 private var selectedId:String?=null
 private var query="";private var filter="";private var job:Job?=null
 private var autoDelivery=false
 override fun onCreate(state:Bundle?) {
  super.onCreate(state);requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
  ui=SafaWidgets(SafaStyle(this));window.statusBarColor=SafaStyle.Palette.cream;window.navigationBarColor=SafaStyle.Palette.cream
  selectedId=state?.getString("selected") ?: intent.getStringExtra("repair_id")
  query=state?.getString("query").orEmpty();filter=state?.getString("filter").orEmpty()
  autoDelivery=state==null && intent.getBooleanExtra("open_delivery",false)
  val host=SafaPageHost(ui,"الصفا للساعات");ui.heading(host.body,"الصيانة","جارٍ التحميل…");setContentView(host.view)
 }
 override fun onResume(){super.onResume();if(::ui.isInitialized) selectedId?.let{details(it)} ?: register()}
 override fun onSaveInstanceState(out:Bundle){out.putString("selected",selectedId);out.putString("query",query);out.putString("filter",filter);super.onSaveInstanceState(out)}
 override fun onDestroy(){scope.cancel();super.onDestroy()}
 @Deprecated("Legacy back callback") override fun onBackPressed(){if(selectedId!=null){selectedId=null;register()}else super.onBackPressed()}
 private fun open(type:Class<*>,id:String?=null){startActivity(Intent(this,type).apply{if(id!=null)putExtra("repair_id",id)})}
 private fun register() {
  job?.cancel()
  val root=ui.column(16).apply{setBackgroundColor(SafaStyle.Palette.cream)}
  ui.heading(root,"سجلات الصيانة","البحث والمتابعة والتسليم")
  val actions=SafaGrid(this,{if(it>=650)4 else 2})
  actions.addView(ui.button("استلام صيانة",true){open(MaintenanceIntakeActivity::class.java)})
  actions.addView(ui.button("صيانة سريعة"){open(QuickRepairActivity::class.java)})
  actions.addView(ui.button("قطع الصيانة"){open(MaintenancePartsActivity::class.java)})
  actions.addView(ui.button("إدارة أزرار الصيانة"){open(QuickRepairButtonsActivity::class.java)})
  ui.add(root,actions)
  val search=ui.input("العميل أو الهاتف أو رقم الفاتورة أو الساعة").apply{setText(query)};ui.add(root,search)
  val states=listOf("" to "الكل","قيد الصيانة" to "قيد الصيانة","بانتظار القطعة" to "بانتظار القطعة","بانتظار التسليم" to "جاهزة للتسليم","تم التسليم" to "تم التسليم")
  val state=Spinner(this).apply{adapter=ArrayAdapter(this@MaintenanceActivity,android.R.layout.simple_spinner_dropdown_item,states.map{it.second});setSelection(states.indexOfFirst{it.first==filter}.coerceAtLeast(0))};ui.add(root,state)
  val notice=ui.text("");ui.add(root,notice)
  val rows=mutableListOf<RepairListRow>()
  val adapter=object:BaseAdapter(){
   override fun getCount()=rows.size
   override fun getItem(p:Int)=rows[p]
   override fun getItemId(p:Int)=p.toLong()
   override fun getView(p:Int,old:View?,parent:ViewGroup):View {
    val r=rows[p];return ui.button("#${r.invoice_number} — ${r.customer.ifBlank{r.watch}}\n${r.watch} · ${MaintenanceDeliveryEligibility.display(r.status)}\n${r.date} · ${r.phone}",height=86){details(r.id)}
   }
  }
  val list=ListView(this).apply{this.adapter=adapter;dividerHeight=ui.style.dp(8)}
  root.addView(list,LinearLayout.LayoutParams(-1,0,1f))
  val more=ui.button("عرض المزيد"){};ui.add(root,more)
  ui.add(root,ui.button("رجوع"){finish()});setContentView(root)
  fun load(reset:Boolean) {
   job?.cancel();val q=query;val f=filter;val offset=if(reset)0 else rows.size
   job=scope.launch {
    more.isEnabled=false;notice.text="جارٍ التحميل…"
    try {
     if(reset)delay(250)
     val page=withContext(Dispatchers.IO){db.maintenanceDao().listPage(q,f,offset)}
     if(reset)rows.clear();rows.addAll(page);adapter.notifyDataSetChanged()
     notice.text=if(rows.isEmpty())"لا توجد سجلات مطابقة" else "السجلات المعروضة: ${rows.size}"
     more.visibility=if(page.size==50)View.VISIBLE else View.GONE
    }catch(e:CancellationException){throw e}catch(e:Exception){notice.text=e.message ?: "تعذر قراءة الصيانة";more.visibility=View.VISIBLE}
    finally{more.isEnabled=true}
   }
  }
  more.setOnClickListener{load(false)}
  ui.listen(search){query=it;load(true)}
  state.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long){val value=states[position].first;if(value!=filter){filter=value;load(true)}}}
  load(true)
 }
 private fun fail(message:String){ui.confirm("الصيانة",message){}.show()}
 private fun details(id:String) {
  job?.cancel();selectedId=id
  job=scope.launch {
   try {
    val repair=withContext(Dispatchers.IO){db.maintenanceDao().repair(id)} ?: error("سجل الصيانة غير موجود")
    val page=MaintenanceDetailPage(ui,repair,
     onBack={selectedId=null;register()},onMessage={MaintenanceMessaging.share(this@MaintenanceActivity,repair)},
     onEdit={open(MaintenanceEditActivity::class.java,id)},onDelivery={deliver(repair)},
     onReturn={open(MaintenanceReturnActivity::class.java,id)},onPrint={open(SalePrintActivity::class.java,id)},
     onMetadataEdit={startActivity(Intent(this@MaintenanceActivity,InvoiceMetadataActivity::class.java).putExtra("kind","repair").putExtra("invoice_id",id))},
     onSave={status,diagnosis,work,notes,button ->
      button.isEnabled=false
      scope.launch {
       try {
        withContext(Dispatchers.IO){
         require(status in listOf("قيد الصيانة","بانتظار القطعة","بانتظار التسليم"))
         val raw=JSONObject(repair.original_json)
         require(repair.status!="تم التسليم" && !raw.has("nativeArchiveDelivery")){"تم تسليم هذه الصيانة"}
         raw.put("status",status).put("workDone",work).put("diagnosis",diagnosis).put("notes",notes).put("statusUpdatedAt",System.currentTimeMillis())
         check(db.maintenanceDao().updateRepairStatus(id,status,raw.toString(),repair.original_json)==1){"تغيرت الفاتورة؛ أعد فتحها"}
        };details(id)
       }catch(e:CancellationException){throw e}catch(e:Exception){fail(e.message ?: "تعذر حفظ الحالة")}
       finally{button.isEnabled=true}
      }
     })
    setContentView(page.view)
    if(autoDelivery){autoDelivery=false;if(MaintenanceDeliveryEligibility.allowed(repair.status,JSONObject(repair.original_json)))deliver(repair)}
   }catch(e:CancellationException){throw e}catch(e:Exception){fail(e.message ?: "تعذر فتح الصيانة")}
  }
 }
 private fun deliver(repair:MaintenanceRepairEntity) {
  scope.launch {
   try {
    val due=withContext(Dispatchers.IO){if(repair.source_sha256=="native8")MaintenanceReturnRules.due(JSONObject(repair.original_json)) else ArchivedRepairDeliveryRepository(db,applicationContext).balance(repair.id)}
    RepairDeliveryDialog.show(this@MaintenanceActivity,repair,due){details(repair.id)}
   }catch(e:CancellationException){throw e}catch(e:Exception){fail(e.message ?: "تعذر فتح التسليم")}
  }
 }
}
