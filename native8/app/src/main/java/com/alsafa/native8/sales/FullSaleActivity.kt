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
            SalesInput("بحث عن الساعة أو الباركود",vm.search){vm.search=it}
            SalesInput("الخصم",vm.discount){vm.discount=it}
            Text("الأصناف: ${vm.lines.size}")
            Button(onClick={startActivity(Intent(this@FullSaleActivity, SalePaymentsActivity::class.java))}){Text("القبض")}
            Text("الحفظ غير مفعل قبل ربط اختيار الأصناف والترحيل المالي الآمن")
        } }
    }
}
