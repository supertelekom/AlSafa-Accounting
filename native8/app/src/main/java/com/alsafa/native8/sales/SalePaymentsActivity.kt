package com.alsafa.native8.sales
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
class SalePaymentsViewModel: ViewModel() {
 var amount by mutableStateOf("")
 var cashbox by mutableStateOf("الدرج")
 var currency by mutableStateOf("USD")
}
class SalePaymentsActivity: ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val vm=ViewModelProvider(this)[SalePaymentsViewModel::class.java]
  setContent { SalesFrame("القبض والدفعات") {
   SalesInput("المبلغ",vm.amount){vm.amount=it}
   SalesInput("الصندوق",vm.cashbox){vm.cashbox=it}
   SalesInput("العملة",vm.currency){vm.currency=it}
   Text("الترحيل المالي قيد الربط")
  } }
 }
}