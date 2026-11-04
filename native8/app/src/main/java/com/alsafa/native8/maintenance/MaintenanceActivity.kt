package com.alsafa.native8.maintenance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.alsafa.native8.sales.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** First operational maintenance screen: historical records and their original details. */
class MaintenanceActivity:ComponentActivity() {
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  var query by mutableStateOf("")
  var records by mutableStateOf<List<MaintenanceRepairEntity>>(emptyList())
  var selected by mutableStateOf<MaintenanceRepairEntity?>(null)
  var error by mutableStateOf("")
  fun refresh() {
   lifecycleScope.launch {
    try {
     records=withContext(Dispatchers.IO) {
      AppDatabase.get(applicationContext).maintenanceDao().search(query)
     }
     error=""
    } catch(e:Exception) { error=e.message ?: "تعذر قراءة سجلات الصيانة" }
   }
  }
  refresh()
  setContent {
   Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement=Arrangement.spacedBy(12.dp)) {
    Text("الصيانة",style=MaterialTheme.typography.headlineSmall)
    OutlinedTextField(value=query,onValueChange={query=it;refresh()},
     label={Text("بحث بالعميل أو الهاتف أو رقم الصيانة أو الساعة")},
     modifier=Modifier.fillMaxWidth(),singleLine=true)
    if(error.isNotBlank()) Text(error)
    selected?.let { repair ->
     OutlinedButton(onClick={selected=null}) { Text("العودة إلى الصيانة") }
     Text("تفاصيل الصيانة",style=MaterialTheme.typography.titleLarge)
     val raw=remember(repair.id) { runCatching { JSONObject(repair.original_json) }.getOrNull() }
     if(raw!=null) {
      val keys=raw.keys()
      while(keys.hasNext()) {
       val key=keys.next()
       val value=raw.opt(key)
       if(value!=null && value!=JSONObject.NULL && key!="watchPhoto" && key!="photos")
        Text(key+": "+value.toString().take(500))
      }
     } else Text("تعذر قراءة بيانات الصيانة الأصلية")
    } ?: run {
     Text("السجلات: "+records.size)
     records.forEach { repair ->
      Card(onClick={selected=repair},modifier=Modifier.fillMaxWidth()) {
       Column(Modifier.padding(12.dp)) {
        Text("#"+repair.invoice_number+" — "+repair.customer,
         style=MaterialTheme.typography.titleMedium)
        Text(repair.watch+" — "+repair.status)
        Text(repair.date+" — "+repair.phone)
       }
      }
     }
     if(records.isEmpty() && error.isBlank()) Text("لا توجد سجلات صيانة مستوردة.")
    }
   }
  }
 }
}
