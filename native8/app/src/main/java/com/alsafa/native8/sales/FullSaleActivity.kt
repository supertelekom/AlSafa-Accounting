package com.alsafa.native8.sales

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class FullSaleViewModel: ViewModel() {
    var customer by mutableStateOf(""); var phone by mutableStateOf(""); var search by mutableStateOf(""); var discount by mutableStateOf("0")
    var quantity by mutableStateOf("1")
    var notice by mutableStateOf("")
    var choices by mutableStateOf<List<SaleChoice>>(emptyList())
    val lines = mutableStateListOf<SaleLine>()
    fun addLine(line: SaleLine) { lines.add(line) }
}

class FullSaleActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this)[FullSaleViewModel::class.java]
        
        
        setContent { SalesFrame("فاتورة مبيع") {
            SalesInput("اسم العميل",vm.customer){vm.customer=it}
            SalesInput("رقم الهاتف",vm.phone){vm.phone=it}
            SalesInput("بحث عن الساعة أو الباركود",vm.search){vm.search=it; vm.choices=SalesProductCatalog(applicationContext).find(it)}
            SalesInput("الكمية",vm.quantity){vm.quantity=it}
            vm.choices.forEach { choice ->
                TextButton(onClick={
                    val qty=vm.quantity.toBigDecimalOrNull()
                    val existing=vm.lines.filter { it.productSource==choice.source && it.productId==choice.productId && it.variantId==choice.variantId }.fold(java.math.BigDecimal.ZERO){a,l->a+l.quantity}
                    if(qty==null || qty<=java.math.BigDecimal.ZERO || qty+existing>choice.available.toBigDecimal()) vm.notice="الكمية غير كافية"
                    else { vm.addLine(SaleLine(choice.source,choice.productId,choice.variantId,choice.title,qty,choice.price.toBigDecimal(),choice.cost.toBigDecimal()));vm.notice="تمت الإضافة" }
                }) { Text(choice.title+" | المتوفر: "+choice.available) }
            }
            Text(vm.notice)
            SalesInput("الخصم",vm.discount){vm.discount=it}
            Text("الأصناف: ${vm.lines.size}")
            Button(onClick={startActivity(Intent(this@FullSaleActivity, SalePaymentsActivity::class.java))}){Text("القبض")}
            Text("الحفظ غير مفعل قبل ربط اختيار الأصناف والترحيل المالي الآمن")
        } }
    }
}
