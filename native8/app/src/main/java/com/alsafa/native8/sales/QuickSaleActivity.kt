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
 var choices by mutableStateOf<List<SaleChoice>>(emptyList())
 var notice by mutableStateOf("")
 val lines=mutableStateListOf<SaleLine>()
}
class QuickSaleActivity: ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val vm=ViewModelProvider(this)[QuickSaleViewModel::class.java]
  setContent { SalesFrame("فاتورة مبسطة") {
   SalesInput("بحث سريع أو باركود",vm.barcode){vm.barcode=it;vm.choices=SalesProductCatalog(applicationContext).find(it)}
   SalesInput("الكمية",vm.quantity){vm.quantity=it}
   vm.choices.forEach { choice ->
    TextButton(onClick={
     val qty=vm.quantity.toBigDecimalOrNull()
     val already=vm.lines.filter{it.productSource==choice.source && it.productId==choice.productId && it.variantId==choice.variantId}.fold(java.math.BigDecimal.ZERO){a,l->a+l.quantity}
     if(qty==null || qty<=java.math.BigDecimal.ZERO || qty+already>choice.available.toBigDecimal()) vm.notice="الكمية غير كافية"
     else {vm.lines.add(SaleLine(choice.source,choice.productId,choice.variantId,choice.title,qty,choice.price.toBigDecimal(),choice.cost.toBigDecimal()));vm.notice="تمت الإضافة"}
    }) {Text(choice.title+" | المتوفر: "+choice.available)}
   }
   Text(vm.notice)
   Text("الأصناف: "+vm.lines.size)
  } }
 }
}