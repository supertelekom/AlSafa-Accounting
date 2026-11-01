package com.alsafa.native8.sales
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
class SaleDetailsViewModel: ViewModel() { var saleId by mutableStateOf("") }
class SaleDetailsActivity: ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val vm=ViewModelProvider(this)[SaleDetailsViewModel::class.java]
  vm.saleId=intent.getStringExtra("sale_id").orEmpty()
  setContent { SalesFrame("تفاصيل الفاتورة") {
   Text("الفاتورة: "+vm.saleId)
   Button(onClick={startActivity(Intent(this@SaleDetailsActivity,SalePrintActivity::class.java).putExtra("sale_id",vm.saleId))}){Text("طباعة / مشاركة")}
   Button(onClick={startActivity(Intent(this@SaleDetailsActivity,SaleReturnsActivity::class.java).putExtra("sale_id",vm.saleId))}){Text("مرتجع / إلغاء")}
  } }
 }
}