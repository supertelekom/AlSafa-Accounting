package com.alsafa.native8.sales
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
class SalePrintViewModel: ViewModel() { var saleId by mutableStateOf(""); var width by mutableStateOf("80") }
class SalePrintActivity: ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val vm = ViewModelProvider(this)[SalePrintViewModel::class.java]
  vm.saleId=intent.getStringExtra("sale_id").orEmpty()
  setContent { SalesFrame("طباعة ومشاركة الفاتورة") { Text("الفاتورة: "+vm.saleId)
SalesInput("عرض الرول (مم)",vm.width){vm.width=it}
Text("قالب PDF الأصلي وBluetooth وWhatsApp Business قيد الربط") } }
 }
}