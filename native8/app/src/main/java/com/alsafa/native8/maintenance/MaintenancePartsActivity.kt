package com.alsafa.native8.maintenance

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

/** Maintenance parts warehouse, based on the v3.9.359 maintenance-parts screen. */
class MaintenancePartsActivity:ComponentActivity() {
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val repo=MaintenancePartsRepository(AppDatabase.get(applicationContext))
  setContent {
   androidx.compose.runtime.CompositionLocalProvider(
    androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
   ) {
    var rows by remember { mutableStateOf<List<MaintenancePartEntity>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MaintenancePartEntity?>(null) }
    var form by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") };var category by remember { mutableStateOf("بطاريات") }
    var barcode by remember { mutableStateOf("") };var stock by remember { mutableStateOf("1") }
    var minStock by remember { mutableStateOf("2") };var currency by remember { mutableStateOf("USD") }
    var cost by remember { mutableStateOf("0") };var salePrice by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") };var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    fun reload() { lifecycleScope.launch { runCatching { withContext(Dispatchers.IO) { repo.list(search) } }
     .onSuccess { rows=it }.onFailure { error=it.message ?: "تعذر قراءة المستودع" } } }
    fun open(part:MaintenancePartEntity?) {
     editing=part;form=true
     val raw=part?.original_json?.let { runCatching { JSONObject(it) }.getOrNull() }
     name=part?.name.orEmpty();category=part?.category ?: "بطاريات"
     barcode=part?.barcode.orEmpty();stock=part?.stock ?: "1"
     minStock=raw?.optString("minStock") ?: "2";currency=part?.currency ?: "USD"
     cost=part?.cost ?: "0";salePrice=part?.sale_price ?: "0"
     notes=raw?.optString("notes").orEmpty()
    }
    LaunchedEffect(search) { reload() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal=12.dp, vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
     Text("قطع الصيانة",style=MaterialTheme.typography.headlineSmall)
     if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
     if(form) {
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(name,{name=it},label={Text("اسم القطعة *")},modifier=Modifier.fillMaxWidth())
      val categories=listOf("بطاريات","أحزمة","زجاج","أقفال","قطع حركة","أخرى")
      Text("النوع")
      androidx.compose.foundation.layout.Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
       categories.take(3).forEach { c -> FilterChip(selected=category==c,onClick={category=c},label={Text(c)}) }
      }
      androidx.compose.foundation.layout.Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
       categories.drop(3).forEach { c -> FilterChip(selected=category==c,onClick={category=c},label={Text(c)}) }
      }
      OutlinedTextField(barcode,{barcode=it},label={Text("باركود قطعة الصيانة")},modifier=Modifier.fillMaxWidth())
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
       OutlinedTextField(stock,{stock=it},label={Text("الكمية بالمخزون *")},modifier=Modifier.weight(1f))
       OutlinedTextField(minStock,{minStock=it},label={Text("حد التنبيه")},modifier=Modifier.weight(1f))
      }
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
       OutlinedTextField(cost,{cost=it},label={Text("كلفة القطعة")},modifier=Modifier.weight(1f))
       OutlinedTextField(salePrice,{salePrice=it},label={Text("سعر البيع")},modifier=Modifier.weight(1f))
      }
      OutlinedTextField(currency,{currency=it},label={Text("عملة القطعة")},modifier=Modifier.fillMaxWidth())
      OutlinedTextField(notes,{notes=it},label={Text("ملاحظات")},modifier=Modifier.fillMaxWidth())
      }
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
       Button(enabled=!busy,onClick={
        busy=true;error=""
        lifecycleScope.launch {
         try { withContext(Dispatchers.IO) { repo.save(editing?.id,name,category,barcode,stock,minStock,currency,cost,salePrice,notes) }
          form=false;reload();Toast.makeText(this@MaintenancePartsActivity,"تم حفظ قطعة الصيانة",Toast.LENGTH_SHORT).show()
         } catch(e:Exception) { error=e.message ?: "تعذر الحفظ" }
         finally { busy=false }
        }
       }) { Text("حفظ") }
       OutlinedButton(onClick={form=false}) { Text("إلغاء") }
      }
     } else {
      Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
       Text("${rows.size} قطعة محفوظة",style=MaterialTheme.typography.bodyMedium)
       Button(onClick={open(null)}) { Text("＋ إضافة قطعة") }
      }
      OutlinedTextField(search,{search=it},singleLine=true,label={Text("ابحث باسم قطعة الصيانة أو الباركود...")},modifier=Modifier.fillMaxWidth())
      LazyColumn(modifier=Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
       items(rows,key={it.id}) { part ->
        val low=runCatching { part.stock.toBigDecimal() <= JSONObject(part.original_json).optString("minStock","2").toBigDecimal() }.getOrDefault(false)
        Card(Modifier.fillMaxWidth()) {
         Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
          Text(part.name,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
          Text("${part.category} — المتاح: ${part.stock} — الباركود: ${part.barcode.ifBlank { "—" }}")
          Text("سعر البيع: ${part.sale_price} ${part.currency} — الكلفة: ${part.cost} ${part.currency}")
          if(low) Text("تنبيه: مخزون القطعة وصل للحد الأدنى",color=MaterialTheme.colorScheme.error)
          Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
           OutlinedButton(onClick={open(part)}) { Text("تعديل") }
           if(part.source_sha256=="native8") TextButton(onClick={
            lifecycleScope.launch {
             try { withContext(Dispatchers.IO) { repo.delete(part.id) };reload() }
             catch(e:Exception) { error=e.message ?: "تعذر الحذف" }
            }
           }) { Text("حذف") }
          }
         }
        }
       }
      }
     }
    }
   }
  }
 }
}
