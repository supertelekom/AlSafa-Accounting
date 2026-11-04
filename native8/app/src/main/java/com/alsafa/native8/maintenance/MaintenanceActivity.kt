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
  if(requestCode==703 && resultCode==RESULT_OK) {
   val id=data?.getStringExtra("repair_id")
   if(id!=null) lifecycleScope.launch { selectedRefresh?.invoke(id) }
  }
 }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  var query by mutableStateOf("")
  var records by mutableStateOf<List<MaintenanceRepairEntity>>(emptyList())
  var selected by mutableStateOf<MaintenanceRepairEntity?>(null)
  var statusChoice by mutableStateOf("قيد الصيانة")
  var workDone by mutableStateOf("")
  var diagnosis by mutableStateOf("")
  var notes by mutableStateOf("")
  var statusSaving by mutableStateOf(false)
  var deliveryDialog by mutableStateOf(false)
  var collection by mutableStateOf("")
  var selectedCashbox by mutableStateOf("drawer")
  var collectionCurrency by mutableStateOf("USD")
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
  // Refresh after returning from the intake form.
  setContent {
   androidx.compose.runtime.CompositionLocalProvider(
    androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
   ) {
    Column(Modifier.fillMaxSize().padding(16.dp),
     verticalArrangement=Arrangement.spacedBy(12.dp)) {
     Text("الصيانة",style=MaterialTheme.typography.headlineSmall)
     if(selected==null) {
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
      OutlinedButton(onClick={selected=null}) { Text("العودة إلى الصيانة") }
      Button(onClick={MaintenanceMessaging.share(this@MaintenanceActivity,repair)}) {
       Text("إرسال تفاصيل الصيانة عبر واتساب أعمال")
      }
      if(repair.source_sha256=="native8" && repair.status!="تم التسليم") {
       Button(onClick={startActivityForResult(Intent(this@MaintenanceActivity,MaintenanceEditActivity::class.java).putExtra("repair_id",repair.id),703)}) { Text("تعديل الفاتورة") }
      }
      if(repair.source_sha256=="native8" && repair.status=="جاهز للاستلام") {
       Button(onClick={collection="";selectedCashbox="drawer";collectionCurrency=repair.currency;deliveryDialog=true}) { Text("تم التسليم — تحصيل المبلغ") }
      }
      if(deliveryDialog && repair.source_sha256=="native8") {
       val raw=remember(repair.original_json) { JSONObject(repair.original_json) }
       val parts=raw.optJSONArray("usedParts") ?: JSONArray()
       val partsTotal=(0 until parts.length()).fold(java.math.BigDecimal.ZERO) { sum,i ->
        val line=parts.optJSONObject(i)
        sum+(line?.optString("qty")?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO)*
          (line?.optString("price")?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO)
       }
       val labor=raw.optString("laborPrice",raw.optString("price","0")).toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
       val due=labor+partsTotal
       AlertDialog(
        onDismissRequest={if(!statusSaving) deliveryDialog=false},
        title={Text("تسليم الساعة وتحصيل المبلغ")},
        text={
         Column(Modifier.fillMaxWidth().heightIn(max=440.dp).verticalScroll(rememberScrollState()),
          verticalArrangement=Arrangement.spacedBy(10.dp)) {
          Text("المبلغ المطلوب تحصيله: "+due.stripTrailingZeros().toPlainString()+" "+repair.currency,
           style=MaterialTheme.typography.titleMedium)
          OutlinedTextField(value=collection,onValueChange={collection=it},
           label={Text("المبلغ المقبوض الآن")},modifier=Modifier.fillMaxWidth(),singleLine=true,
           keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal))
          Text("الصندوق",style=MaterialTheme.typography.labelLarge)
          var cashboxExpanded by remember { mutableStateOf(false) }
          val cashboxes=listOf("drawer" to "الدرج","ChamCash" to "شام كاش","external" to "الصندوق الخارجي","bank" to "البنك")
          Box {
           OutlinedButton(onClick={cashboxExpanded=true},modifier=Modifier.fillMaxWidth()) {
            Text(cashboxes.first { it.first==selectedCashbox }.second)
           }
           DropdownMenu(expanded=cashboxExpanded,onDismissRequest={cashboxExpanded=false}) {
            cashboxes.forEach { (key,label) ->
             DropdownMenuItem(text={Text(label)},onClick={selectedCashbox=key;cashboxExpanded=false})
            }
           }
          }
          Text("العملة",style=MaterialTheme.typography.labelLarge)
          var currencyExpanded by remember { mutableStateOf(false) }
          val currencies=listOf("USD" to "دولار","TRY" to "تركي","SYP" to "سوري")
          Box {
           OutlinedButton(onClick={currencyExpanded=true},modifier=Modifier.fillMaxWidth()) {
            Text(currencies.first { it.first==collectionCurrency }.second)
           }
           DropdownMenu(expanded=currencyExpanded,onDismissRequest={currencyExpanded=false}) {
            currencies.forEach { (key,label) ->
             DropdownMenuItem(text={Text(label)},onClick={collectionCurrency=key;currencyExpanded=false})
            }
           }
          }
          Text("يُحسب المعادل تلقائياً بسعر الصرف المحفوظ في التطبيق",
           style=MaterialTheme.typography.bodySmall)
         }
        },
        confirmButton={Button(enabled=!statusSaving && collection.toBigDecimalOrNull()?.signum()==1,onClick={
         lifecycleScope.launch {
          statusSaving=true
          try {
           withContext(Dispatchers.IO) {
            val payment=MaintenanceDeliveryRepository.PaymentLine(collection,collectionCurrency,selectedCashbox,"")
            MaintenanceDeliveryRepository(AppDatabase.get(applicationContext),applicationContext).deliver(repair.id,listOf(payment))
           }
           deliveryDialog=false
           selectedRefresh?.invoke(repair.id)
           Toast.makeText(this@MaintenanceActivity,"تم تسليم الصيانة وتسجيل التحصيل",Toast.LENGTH_LONG).show()
          } catch(e:Exception) {
           error=e.message ?: "تعذر التسليم"
           Toast.makeText(this@MaintenanceActivity,error,Toast.LENGTH_LONG).show()
          } finally { statusSaving=false }
         }
        }) {Text("تأكيد التسليم والقبض")}},
        dismissButton={TextButton(onClick={deliveryDialog=false}) {Text("إلغاء")}}
       )
      }
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
       verticalArrangement=Arrangement.spacedBy(12.dp)) {
       MaintenanceDetail(repair)
       if(repair.source_sha256=="native8" && repair.status!="تم التسليم") {
        Text("تحديث حالة الصيانة",style=MaterialTheme.typography.titleMedium)
        listOf("قيد الصيانة","بانتظار القطعة","جاهز للاستلام").forEach { option ->
         Row {
          RadioButton(selected=statusChoice==option,onClick={statusChoice=option})
          Text(option)
         }
        }
        OutlinedTextField(value=diagnosis,onValueChange={diagnosis=it},
         label={Text("التشخيص")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(value=workDone,onValueChange={workDone=it},
         label={Text("العمل المنجز")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(value=notes,onValueChange={notes=it},
         label={Text("ملاحظات")},modifier=Modifier.fillMaxWidth())
        Button(enabled=!statusSaving,onClick={
         lifecycleScope.launch {
          statusSaving=true
          error=""
          try {
           val updated=withContext(Dispatchers.IO) {
            val db=AppDatabase.get(applicationContext)
            val latest=db.maintenanceDao().repair(repair.id) ?: error("سجل الصيانة غير موجود")
            require(latest.source_sha256=="native8" && latest.status!="تم التسليم") { "السجل غير قابل للتعديل" }
            require(statusChoice in listOf("قيد الصيانة","بانتظار القطعة","جاهز للاستلام")) { "حالة غير مدعومة" }
            if(statusChoice=="جاهز للاستلام") require(workDone.isNotBlank()) { "سجل العمل المنجز قبل اعتماد الجاهزية" }
            val raw=JSONObject(latest.original_json)
            raw.put("status",statusChoice)
            raw.put("workDone",workDone)
            raw.put("diagnosis",diagnosis)
            raw.put("notes",notes)
            raw.put("statusUpdatedAt",System.currentTimeMillis())
            check(db.maintenanceDao().updateRepairStatus(repair.id,statusChoice,raw.toString())==1)
            db.maintenanceDao().repair(repair.id)
           }
           selected=updated
           refresh()
           Toast.makeText(this@MaintenanceActivity,"تم حفظ حالة الصيانة بنجاح",Toast.LENGTH_LONG).show()
          } catch(e:Exception) {
           error=e.message ?: "تعذر تحديث الحالة"
           Toast.makeText(this@MaintenanceActivity,error,Toast.LENGTH_LONG).show()
          }
          finally { statusSaving=false }
         }
        }) { Text("حفظ تحديث الحالة") }
       }
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

@Composable
private fun MaintenanceDetail(repair:MaintenanceRepairEntity) {
 val obj=remember(repair.id,repair.original_json) {
  runCatching { JSONObject(repair.original_json) }.getOrNull()
 }
 Text("تفاصيل الصيانة رقم "+repair.invoice_number,style=MaterialTheme.typography.headlineSmall)
 if(obj==null) { Text("تعذر قراءة تفاصيل الصيانة الأصلية");return }
 @Composable fun field(label:String,vararg keys:String) {
  val value=keys.firstNotNullOfOrNull { key ->
   obj.opt(key)?.takeUnless { it==JSONObject.NULL || it is JSONObject || it is JSONArray }
    ?.toString()?.takeIf { it.isNotBlank() }
  }
  if(value!=null) {
   Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(3.dp)) {
    Text(label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value,style=MaterialTheme.typography.bodyLarge)
   }
  }
 }
 @Composable fun section(title:String,content:@Composable ColumnScope.()->Unit) {
  Card(Modifier.fillMaxWidth()) {
   Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
    Text(title,style=MaterialTheme.typography.titleMedium)
    content()
   }
  }
 }
 section("بيانات الاستلام") {
  field("رقم الصيانة","invoiceNo")
  field("التاريخ","date")
  field("الوقت","time")
  field("العميل","customer")
  field("الهاتف","phone")
  field("الساعة","watch")
  field("خدمة الصيانة السريعة","quickServiceName")
  field("الموديل","model")
  field("العطل","problem")
  field("التشخيص","diagnosis")
  field("العمل المنجز","workDone")
  field("الحالة","status")
 }
 val linesForTotals=obj.optJSONArray("usedParts") ?: JSONArray()
 val calculatedParts=(0 until linesForTotals.length()).sumOf { i ->
  val part=linesForTotals.optJSONObject(i)
  (part?.optString("qty")?.toDoubleOrNull() ?: 0.0)*(part?.optString("price")?.toDoubleOrNull() ?: 0.0)
 }
 val calculatedCost=(0 until linesForTotals.length()).sumOf { i ->
  val part=linesForTotals.optJSONObject(i)
  (part?.optString("qty")?.toDoubleOrNull() ?: 0.0)*(part?.optString("cost")?.toDoubleOrNull() ?: 0.0)
 }
 val labor=(if(obj.has("laborPrice")) obj.optString("laborPrice") else obj.optString("price","0")).toDoubleOrNull() ?: 0.0
 section("التكلفة والقبض") {
  Text("الإجمالي المحسوب (أجرة العمل + القطع): "+(labor+calculatedParts)+" "+repair.currency,
   style=MaterialTheme.typography.titleMedium)
  Text("تكلفة القطع المحسوبة: "+calculatedCost+" "+repair.currency)

  field("العملة","currency")
  field("إجمالي الفاتورة المسجل","price")
  field("أجرة العمل","laborPrice")
  field("قيمة القطع","partsTotal")
  field("تكلفة القطع","partsCost")
  field("التكلفة الإجمالية المسجلة","cost")
  field("تكلفة إضافية","manualCost")
  field("الضريبة","tax")
  field("المقبوض","received")
  field("حالة الدفع","paymentStatus")
  field("طريقة الدفع","paymentMethod")
 }
 val parts=obj.optJSONArray("usedParts")
 if(parts!=null && parts.length()>0) {
  section("قطع الصيانة المستخدمة") {
   for(i in 0 until parts.length()) {
    val part=parts.optJSONObject(i)
    if(part!=null) {
     val name=part.optString("name").ifBlank { "قطعة "+(i+1) }
     Text(name,style=MaterialTheme.typography.bodyLarge)
     listOf("qty" to "الكمية","quantity" to "الكمية",
      "price" to "السعر","cost" to "التكلفة").forEach { (key,label) ->
      if(part.has(key)) Text(label+": "+part.optString(key))
     }
     if(i<parts.length()-1) HorizontalDivider()
    } else Text(parts.optString(i))
   }
  }
 }
 section("التسليم والملاحظات") {
  field("تاريخ التسليم","deliveredDate")
  field("وقت التسليم","deliveredTime")
  field("ملاحظات","notes")
 }
 val imageValue=obj.optString("watchPhoto")
 if(imageValue.isNotBlank()) {
  section("صورة الساعة") {
   AndroidView(factory={ context -> ImageView(context).apply {
    adjustViewBounds=true
    scaleType=ImageView.ScaleType.FIT_CENTER
   } },update={ view -> MaintenancePhoto.display(view,imageValue) },
    modifier=Modifier.fillMaxWidth().heightIn(max=350.dp))
  }
 }
 val photoCount=listOf("photos","watchPhotos","images").sumOf { key ->
  obj.optJSONArray(key)?.length() ?: 0
 }
 if(photoCount>0) Text("صور الصيانة المحفوظة: "+photoCount+" — عرض الصور قيد الربط")
}
