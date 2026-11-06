package com.alsafa.native8.maintenance

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.alsafa.native8.sales.AppDatabase
import com.alsafa.native8.sales.ExchangeRateSettings
import java.math.RoundingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Historical maintenance records remain immutable; this screen presents the original fields. */
class MaintenanceActivity:ComponentActivity() {
 private var reload: (() -> Unit)?=null
 private var selectedRefresh: (suspend (String)->Unit)?=null
 override fun onResume() { super.onResume();reload?.invoke() }
 @Deprecated("Result API integration pending")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==702 && resultCode==RESULT_OK) reload?.invoke()
  if(requestCode in listOf(703,704,705) && resultCode==RESULT_OK) {
   val id=data?.getStringExtra("repair_id")
   if(id!=null) lifecycleScope.launch { selectedRefresh?.invoke(id) }
  }
 }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
  var query by mutableStateOf("")
  var records by mutableStateOf<List<MaintenanceRepairEntity>>(emptyList())
  var selected by mutableStateOf<MaintenanceRepairEntity?>(null)
  var statusChoice by mutableStateOf("قيد الصيانة")
  var workDone by mutableStateOf("")
  var diagnosis by mutableStateOf("")
  var notes by mutableStateOf("")
  var statusSaving by mutableStateOf(false)
  var deliveryDialog by mutableStateOf(false)
  var error by mutableStateOf("")
  var loading by mutableStateOf(false)
  var generation=0
  fun refresh() {
   val request=++generation
   lifecycleScope.launch {
    loading=true
    try {
     val result=withContext(Dispatchers.IO) {
      AppDatabase.get(applicationContext).maintenanceDao().search(query)
     }
     if(request==generation) { records=result;error="" }
    } catch(e:Exception) {
     if(request==generation) error=e.message ?: "تعذر قراءة سجلات الصيانة"
    } finally { if(request==generation) loading=false }
   }
  }
  selectedRefresh={ id ->
   selected=withContext(Dispatchers.IO) { AppDatabase.get(applicationContext).maintenanceDao().repair(id) }
   selected?.let { current ->
    statusChoice=current.status
    val raw=runCatching { JSONObject(current.original_json) }.getOrNull()
    workDone=raw?.optString("workDone").orEmpty()
    diagnosis=raw?.optString("diagnosis").orEmpty()
    notes=raw?.optString("notes").orEmpty()
   }
   refresh()
  }
  reload=::refresh
  refresh()
  intent.getStringExtra("repair_id")?.let { id -> lifecycleScope.launch {
   selectedRefresh?.invoke(id)
   selected?.let { r -> if(intent.getBooleanExtra("open_delivery",false) && r.source_sha256=="native8" && MaintenanceDeliveryEligibility.allowed(r.status,JSONObject(r.original_json))) {
    deliveryDialog=true
   } }
  } }
  // Refresh after returning from the intake form.
  setContent {
   androidx.compose.runtime.CompositionLocalProvider(
    androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
   ) {
    Column(Modifier.fillMaxSize().padding(if(selected==null)16.dp else 0.dp),
     verticalArrangement=Arrangement.spacedBy(12.dp)) {
     if(selected==null) {
      Text("الصيانة",style=MaterialTheme.typography.headlineSmall)
      Button(onClick={startActivity(Intent(this@MaintenanceActivity,QuickRepairActivity::class.java))}) { Text("صيانة سريعة") }
      OutlinedButton(onClick={startActivity(Intent(this@MaintenanceActivity,MaintenancePartsActivity::class.java))}) { Text("قطع الصيانة") }
      Button(onClick={
       startActivityForResult(Intent(this@MaintenanceActivity,MaintenanceIntakeActivity::class.java),702)
      }) { Text("استلام صيانة") }
      OutlinedTextField(value=query,onValueChange={query=it;refresh()},
       label={Text("بحث بالعميل أو الهاتف أو رقم الصيانة أو الساعة")},
       modifier=Modifier.fillMaxWidth(),singleLine=true)
     }
     if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
     if(loading) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
     val repair=selected
     if(repair!=null) {
      key(repair.id,repair.original_json) {
       AndroidView(modifier=Modifier.weight(1f).fillMaxWidth(),factory={ context ->
        MaintenanceDetailPage(com.alsafa.native8.stylekit.SafaWidgets(com.alsafa.native8.stylekit.SafaStyle(context)),repair,
         onBack={selected=null},onMessage={MaintenanceMessaging.share(this@MaintenanceActivity,repair)},
         onEdit={startActivityForResult(Intent(this@MaintenanceActivity,MaintenanceEditActivity::class.java).putExtra("repair_id",repair.id),703)},
         onDelivery={RepairDeliveryDialog.show(this@MaintenanceActivity,repair,com.alsafa.native8.sales.LegacyMaintenanceProfitReader.total(JSONObject(repair.original_json))) {lifecycleScope.launch {selectedRefresh?.invoke(repair.id)}}},
         onReturn={startActivityForResult(Intent(this@MaintenanceActivity,MaintenanceReturnActivity::class.java).putExtra("repair_id",repair.id),704)},
         onPrint={startActivity(Intent(this@MaintenanceActivity,com.alsafa.native8.sales.SalePrintActivity::class.java).putExtra("repair_id",repair.id))},
         onSave={status,diagnosisValue,work,notesValue,button ->
          button.isEnabled=false
          lifecycleScope.launch {
           try {
            withContext(Dispatchers.IO) {
             val db=AppDatabase.get(applicationContext)
             val latest=db.maintenanceDao().repair(repair.id) ?: error("سجل الصيانة غير موجود")
             require(latest.source_sha256=="native8" && latest.status!="تم التسليم") {"السجل غير قابل للتعديل"}
             require(status in listOf("قيد الصيانة","بانتظار القطعة","جاهز للاستلام"))
             if(status=="جاهز للاستلام")require(work.isNotBlank()) {"سجل العمل المنجز قبل اعتماد الجاهزية"}
             val raw=JSONObject(latest.original_json).put("status",status).put("workDone",work).put("diagnosis",diagnosisValue).put("notes",notesValue).put("statusUpdatedAt",System.currentTimeMillis())
             check(db.maintenanceDao().updateRepairStatus(repair.id,status,raw.toString())==1)
            }
            selectedRefresh?.invoke(repair.id)
            Toast.makeText(this@MaintenanceActivity,"تم حفظ حالة الصيانة بنجاح",Toast.LENGTH_LONG).show()
           } catch(e:Exception){error=e.message ?: "تعذر تحديث الحالة"}
           finally {button.isEnabled=true}
          }
         }).view
       })
      }
      if(deliveryDialog) LaunchedEffect(repair.id) {
       deliveryDialog=false
       RepairDeliveryDialog.show(this@MaintenanceActivity,repair,com.alsafa.native8.sales.LegacyMaintenanceProfitReader.total(JSONObject(repair.original_json))) {lifecycleScope.launch {selectedRefresh?.invoke(repair.id)}}
      }
     } else {
      Text("السجلات المعروضة: "+records.size,style=MaterialTheme.typography.titleMedium)
      LazyColumn(modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
       items(records,key={it.id}) { entry ->
       Card(onClick={
        selected=entry
        statusChoice=entry.status
        val raw=runCatching { JSONObject(entry.original_json) }.getOrNull()
        workDone=raw?.optString("workDone").orEmpty()
        diagnosis=raw?.optString("diagnosis").orEmpty()
        notes=raw?.optString("notes").orEmpty()
       },modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
         Text("صيانة رقم "+entry.invoice_number,style=MaterialTheme.typography.titleMedium)
         Text(entry.customer.ifBlank { entry.watch.ifBlank { "صيانة سريعة" } },
          maxLines=2,overflow=TextOverflow.Ellipsis)
         Text(entry.watch+" — "+entry.status,maxLines=2,overflow=TextOverflow.Ellipsis)
         Text(entry.date+(if(entry.phone.isNotBlank()) " — "+entry.phone else ""))
        }
       }
      }
      }
      if(records.isEmpty() && !loading && error.isBlank()) Text("لا توجد سجلات صيانة مطابقة.")
     }
    }
   }
  }
 }
}
