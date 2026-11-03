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
class SaleDetailsViewModel: ViewModel() { var saleId by mutableStateOf(""); var lines by mutableStateOf<List<SalesEngine.InvoiceLine>>(emptyList()); var total by mutableStateOf<String?>(null); var paid by mutableStateOf<String?>(null); var due by mutableStateOf<String?>(null); var status by mutableStateOf(""); var busy by mutableStateOf(false); var recognizedProfit by mutableStateOf<String?>(null); var reversedProfit by mutableStateOf<String?>(null); var netProfit by mutableStateOf<String?>(null) }
class SaleDetailsActivity: ComponentActivity() {
 private lateinit var detailsVm: SaleDetailsViewModel
 override fun onResume() {
  super.onResume()
  if (::detailsVm.isInitialized && detailsVm.saleId.isNotBlank()) {
   lifecycleScope.launch {
    val vm = detailsVm
    val db = AppDatabase.get(applicationContext)
    val sale = db.salesPostingDao().sale(vm.saleId) ?: return@launch
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
   if (vm.status != "ملغاة" && vm.total != null) {
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
   Button(enabled=vm.status != "ملغاة",onClick={startActivity(Intent(this@SaleDetailsActivity,SalePaymentsActivity::class.java).putExtra("sale_id",vm.saleId))}){Text("تحصيل دفعة")}
   Button(onClick={startActivity(Intent(this@SaleDetailsActivity,SalePrintActivity::class.java).putExtra("sale_id",vm.saleId))}){Text("طباعة / مشاركة")}
   Button(onClick={startActivity(Intent(this@SaleDetailsActivity,SaleReturnsActivity::class.java).putExtra("sale_id",vm.saleId))}){Text("مرتجع / إلغاء")}
  } }
 }
}