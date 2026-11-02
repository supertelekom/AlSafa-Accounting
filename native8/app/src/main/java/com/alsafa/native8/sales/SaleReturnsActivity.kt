package com.alsafa.native8.sales
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
class SaleReturnsViewModel: ViewModel() { var saleId by mutableStateOf(""); var quantity by mutableStateOf(""); var reason by mutableStateOf(""); var lineId by mutableStateOf(""); var status by mutableStateOf("") }
class SaleReturnsActivity: ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val vm = ViewModelProvider(this)[SaleReturnsViewModel::class.java]
  vm.saleId=intent.getStringExtra("sale_id").orEmpty()
  vm.lineId=intent.getStringExtra("line_id").orEmpty()
  setContent { SalesFrame("المرتجع والإلغاء") { Text("الفاتورة: "+vm.saleId)
if(vm.lineId.isBlank()) Text("اختر البند من شاشة تفاصيل الفاتورة أولاً")
else Text("تم تحديد بند الفاتورة")
SalesInput("الكمية المرتجعة",vm.quantity){vm.quantity=it}
SalesInput("السبب",vm.reason){vm.reason=it}
Button(enabled=vm.lineId.isNotBlank(),onClick={
 try { val id=SalesEngine(applicationContext).returnLine(vm.saleId,vm.lineId,vm.quantity.toBigDecimal(),vm.reason);vm.status="تم تسجيل المرتجع: "+id }
 catch(e:Exception){vm.status=e.message ?: "تعذر المرتجع"}
}){Text("تسجيل المرتجع")}
Text(vm.status) } }
 }
}