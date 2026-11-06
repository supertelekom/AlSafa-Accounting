package com.alsafa.native8.sales
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import org.json.JSONObject
class SaleDetailsViewModel: ViewModel() { var saleId by mutableStateOf(""); var lines by mutableStateOf<List<SalesEngine.InvoiceLine>>(emptyList()); var total by mutableStateOf<String?>(null); var paid by mutableStateOf<String?>(null); var due by mutableStateOf<String?>(null); var status by mutableStateOf(""); var busy by mutableStateOf(false); var historical by mutableStateOf(false); var legacyRaw by mutableStateOf(""); var error by mutableStateOf(""); var recognizedProfit by mutableStateOf<String?>(null); var reversedProfit by mutableStateOf<String?>(null); var netProfit by mutableStateOf<String?>(null) }
class SaleDetailsActivity: ComponentActivity() {
 private lateinit var detailsVm: SaleDetailsViewModel
 override fun onResume() {
  super.onResume()
  if (::detailsVm.isInitialized && detailsVm.saleId.isNotBlank()) {
   lifecycleScope.launch {
    val vm = detailsVm
    val db = AppDatabase.get(applicationContext)
    val sale = db.salesPostingDao().sale(vm.saleId)
    if (sale == null) {
     val legacy=db.legacySaleDao().legacyById(vm.saleId)
     if (legacy == null) { vm.error="الفاتورة غير موجودة"; return@launch }
     vm.historical=true
     vm.legacyRaw=legacy.original_json
     vm.total=legacy.total
     vm.paid=legacy.historical_paid
     vm.due=legacy.due
     vm.status=legacy.status
     vm.lines=emptyList()
     return@launch
    }
    vm.historical=false
    vm.legacyRaw=""
    val debt = db.legacyReceivablesDao().bySaleId(vm.saleId)
    val audit = db.salesFinancialAuditDao()
    val earned = audit.profits(vm.saleId).fold(BigDecimal.ZERO) { total, event -> total + event.recognized_profit.toBigDecimal() }
    val reversed = audit.reversals(vm.saleId).fold(BigDecimal.ZERO) { total, event -> total + event.reversed_profit.toBigDecimal() }
    vm.lines = SalesEngine(applicationContext).lines(vm.saleId)
    vm.recognizedProfit = earned.setScale(2, RoundingMode.HALF_UP).toPlainString()
    vm.reversedProfit = reversed.setScale(2, RoundingMode.HALF_UP).toPlainString()
    vm.netProfit = (earned - reversed).setScale(2, RoundingMode.HALF_UP).toPlainString()
    vm.status = sale.status
    vm.total = sale.total
    vm.paid = sale.paid
    vm.due = debt?.balance ?: (sale.total.toBigDecimal() - sale.paid.toBigDecimal()).max(BigDecimal.ZERO).toPlainString()
   }
  }
 }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val vm=ViewModelProvider(this)[SaleDetailsViewModel::class.java]
  vm.saleId=intent.getStringExtra("sale_id").orEmpty()
  detailsVm = vm
  setContent { SalesFrame("تفاصيل الفاتورة") {
   Text("الفاتورة: "+vm.saleId)
   if(vm.error.isNotBlank()) Text(vm.error)
   if (vm.total != null) {
    Card(modifier=Modifier.fillMaxWidth()) {
     Column(modifier=Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
      Text("الصافي: "+vm.total)
      Text("المدفوع: "+vm.paid)
      Text("المتبقي: "+vm.due, style=MaterialTheme.typography.titleLarge)
      if (vm.netProfit != null) {
       Text("الربح المحقق من القبض: "+vm.recognizedProfit+" USD")
       Text("الأرباح المعكوسة للمرتجعات والإلغاء: "+vm.reversedProfit+" USD")
       Text("صافي الربح المحقق: "+vm.netProfit+" USD", style=MaterialTheme.typography.titleLarge)
      }
     }
    }
   }
   if (vm.status.isNotBlank()) Text("الحالة: "+vm.status)
   if(vm.historical && vm.legacyRaw.isNotBlank()) {
    val raw=remember(vm.legacyRaw) { runCatching { JSONObject(vm.legacyRaw) }.getOrNull() }
    raw?.let { invoice ->
     Text("فاتورة مبيع قديمة",style=MaterialTheme.typography.titleLarge)
     Text("رقم الفاتورة: "+invoice.optString("invoiceNo"))
     Text("التاريخ: "+invoice.optString("date")+" "+invoice.optString("time"))
     Text("العميل: "+invoice.optString("customer"))
     Text("الهاتف: "+invoice.optString("phone"))
     Text("العملة: "+invoice.optString("currency","USD"))
     val items=invoice.optJSONArray("items")
     if(items!=null) for(i in 0 until items.length()) {
      val item=items.optJSONObject(i) ?: continue
      Card(modifier=Modifier.fillMaxWidth()) {
       Column(modifier=Modifier.padding(16.dp)) {
        Text(item.optString("name",item.optString("title",item.optString("productName","صنف "+(i+1)))))
        Text("الكمية: "+item.optString("qty",item.optString("quantity")))
        Text("السعر: "+item.optString("price",item.optString("unitPrice")))
       }
      }
     }
    }
    Button(onClick={startActivity(Intent(this@SaleDetailsActivity,InvoiceMetadataActivity::class.java).putExtra("kind","sale").putExtra("invoice_id",vm.saleId))}){Text("تعديل بيانات الفاتورة")}
   }
   vm.lines.forEach { line ->
    Card(modifier=Modifier.fillMaxWidth()) {
     Column(modifier=Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
      Text(line.title, style=MaterialTheme.typography.titleMedium)
      Text("الكمية: "+line.quantity+"     المرتجع: "+line.returned)
      Text("السعر: "+line.price)
      Button(onClick={
       startActivity(Intent(this@SaleDetailsActivity,SaleReturnsActivity::class.java)
        .putExtra("sale_id",vm.saleId).putExtra("line_id",line.id))
      }) { Text("مرتجع هذا البند") }
     }
    }
   }
   if (!vm.historical && vm.status != "ملغاة" && vm.total != null) {
    Button(enabled = !vm.busy && vm.total != null,
     onClick = {
      vm.busy = true
      lifecycleScope.launch {
       try {
        RoomSalesEngine(applicationContext).cancelWithRefund(vm.saleId)
        vm.status = "ملغاة"
        vm.due = "0"
        val audit = AppDatabase.get(applicationContext).salesFinancialAuditDao()
        val earned = audit.profits(vm.saleId).fold(BigDecimal.ZERO) { total, event -> total + event.recognized_profit.toBigDecimal() }
        val reversed = audit.reversals(vm.saleId).fold(BigDecimal.ZERO) { total, event -> total + event.reversed_profit.toBigDecimal() }
        vm.recognizedProfit = earned.setScale(2, RoundingMode.HALF_UP).toPlainString()
        vm.reversedProfit = reversed.setScale(2, RoundingMode.HALF_UP).toPlainString()
        vm.netProfit = (earned - reversed).setScale(2, RoundingMode.HALF_UP).toPlainString()
        vm.lines = RoomSalesEngine(applicationContext).lines(vm.saleId)
       } catch(e: Exception) { vm.status = e.message ?: "تعذر الإلغاء" }
       finally { vm.busy = false }
      }
     }) { Text("إلغاء الفاتورة وتسوية المقبوض") }
   }
   if(!vm.historical) Button(enabled=vm.status != "ملغاة",onClick={startActivity(Intent(this@SaleDetailsActivity,SalePaymentsActivity::class.java).putExtra("sale_id",vm.saleId))}){Text("تحصيل دفعة")}
   Button(onClick={startActivity(Intent(this@SaleDetailsActivity,SalePrintActivity::class.java).putExtra("sale_id",vm.saleId))}){Text("طباعة / مشاركة")}
   if(!vm.historical) Button(onClick={startActivity(Intent(this@SaleDetailsActivity,SaleReturnsActivity::class.java).putExtra("sale_id",vm.saleId))}){Text("مرتجع / إلغاء")}
  } }
 }
}