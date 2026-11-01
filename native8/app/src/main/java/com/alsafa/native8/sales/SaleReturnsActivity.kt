package com.alsafa.native8.sales
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
class SaleReturnsViewModel: ViewModel() { var saleId by mutableStateOf(""); var quantity by mutableStateOf(""); var reason by mutableStateOf("") }
class SaleReturnsActivity: ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val vm = ViewModelProvider(this)[SaleReturnsViewModel::class.java]
  vm.saleId=intent.getStringExtra("sale_id").orEmpty()
  setContent { SalesFrame("المرتجع والإلغاء") { Text("الفاتورة: "+vm.saleId)
SalesInput("الكمية المرتجعة",vm.quantity){vm.quantity=it}
SalesInput("السبب",vm.reason){vm.reason=it}
Text("الترحيل ينتظر دفتر حركات موحداً") } }
 }
}