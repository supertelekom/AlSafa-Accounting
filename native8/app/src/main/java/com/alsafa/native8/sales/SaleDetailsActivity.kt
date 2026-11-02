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
class SaleDetailsViewModel: ViewModel() { var saleId by mutableStateOf(""); var lines by mutableStateOf<List<SalesEngine.InvoiceLine>>(emptyList()) }
class SaleDetailsActivity: ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val vm=ViewModelProvider(this)[SaleDetailsViewModel::class.java]
  vm.saleId=intent.getStringExtra("sale_id").orEmpty()
  vm.lines=SalesEngine(applicationContext).lines(vm.saleId)
  setContent { SalesFrame("تفاصيل الفاتورة") {
   Text("الفاتورة: "+vm.saleId)
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