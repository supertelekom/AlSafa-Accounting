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
class SaleDetailsViewModel: ViewModel() { var saleId by mutableStateOf(""); var lines by mutableStateOf<List<SalesEngine.InvoiceLine>>(emptyList()); var total by mutableStateOf<String?>(null); var paid by mutableStateOf<String?>(null); var due by mutableStateOf<String?>(null) }
class SaleDetailsActivity: ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val vm=ViewModelProvider(this)[SaleDetailsViewModel::class.java]
  vm.saleId=intent.getStringExtra("sale_id").orEmpty()
  vm.lines=SalesEngine(applicationContext).lines(vm.saleId)
  lifecycleScope.launch {
   val db = AppDatabase.get(applicationContext)
   val sale = db.salesPostingDao().sale(vm.saleId)
   val debt = db.legacyReceivablesDao().bySaleId(vm.saleId)
   if (sale != null) {
    vm.total = sale.total
    vm.paid = sale.paid
    vm.due = debt?.balance ?: (sale.total.toBigDecimal() - sale.paid.toBigDecimal()).max(java.math.BigDecimal.ZERO).toPlainString()
   }
  }
  setContent { SalesFrame("تفاصيل الفاتورة") {
   Text("الفاتورة: "+vm.saleId)
   if (vm.total != null) {
    Card(modifier=Modifier.fillMaxWidth()) {
     Column(modifier=Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
      Text("الصافي: "+vm.total)
      Text("المدفوع: "+vm.paid)
      Text("المتبقي: "+vm.due, style=MaterialTheme.typography.titleLarge)
     }
    }
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
   Button(onClick={startActivity(Intent(this@SaleDetailsActivity,SalePaymentsActivity::class.java).putExtra("sale_id",vm.saleId))}){Text("تحصيل دفعة")}
   Button(onClick={startActivity(Intent(this@SaleDetailsActivity,SalePrintActivity::class.java).putExtra("sale_id",vm.saleId))}){Text("طباعة / مشاركة")}
   Button(onClick={startActivity(Intent(this@SaleDetailsActivity,SaleReturnsActivity::class.java).putExtra("sale_id",vm.saleId))}){Text("مرتجع / إلغاء")}
  } }
 }
}