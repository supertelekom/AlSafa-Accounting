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
 var saleId by mutableStateOf("")
 var status by mutableStateOf("")
}
class SalePaymentsActivity: ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val vm=ViewModelProvider(this)[SalePaymentsViewModel::class.java]
  vm.saleId=intent.getStringExtra("sale_id").orEmpty()
  setContent { SalesFrame("القبض والدفعات") {
   SalesInput("المبلغ",vm.amount){vm.amount=it}
   SalesInput("الصندوق",vm.cashbox){vm.cashbox=it}
   SalesInput("العملة",vm.currency){vm.currency=it}
   Text("الفاتورة: "+vm.saleId)
   Button(onClick={
    try {
     require(vm.saleId.isNotBlank()) { "اختر فاتورة محفوظة" }
     val id=SalesEngine(applicationContext).collect(vm.saleId,SalePayment(vm.cashbox,vm.currency,vm.amount.toBigDecimal()))
     vm.status="تم تسجيل القبض: "+id
    } catch(e:Exception) {vm.status=e.message ?: "تعذر القبض"}
   }) {Text("تسجيل دفعة")}
   Text(vm.status)
   if(vm.saleId.isBlank()) Button(onClick={
     try { vm.saleId=SalesPostingActions(applicationContext).postDraft(); vm.status="تم حفظ الفاتورة" }
     catch(e:Exception){ vm.status=e.message ?: "تعذر حفظ الفاتورة" }
   }) { Text("حفظ الفاتورة") }
  } }
 }
}