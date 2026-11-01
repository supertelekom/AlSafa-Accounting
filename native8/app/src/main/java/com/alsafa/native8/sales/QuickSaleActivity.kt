package com.alsafa.native8.sales
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class QuickSaleViewModel: ViewModel() {
 var barcode by mutableStateOf("")
 var quantity by mutableStateOf("1")
}
class QuickSaleActivity: ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val vm=ViewModelProvider(this)[QuickSaleViewModel::class.java]
  setContent { SalesFrame("فاتورة مبسطة") {
   SalesInput("بحث سريع أو باركود",vm.barcode){vm.barcode=it}
   SalesInput("الكمية",vm.quantity){vm.quantity=it}
   Text("الحفظ ينتظر ربط محرك المبيعات")
  } }
 }
}