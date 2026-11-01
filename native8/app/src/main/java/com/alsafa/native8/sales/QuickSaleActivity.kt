package com.alsafa.native8.sales

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class QuickSaleViewModel: ViewModel() {
    var query by mutableStateOf("")
    var quantity by mutableStateOf("1")
    var choices by mutableStateOf<List<SaleChoice>>(emptyList())
    var notice by mutableStateOf("")
    fun search(value: String, catalog: SalesProductCatalog) {
        query=value; choices=catalog.find(value)
    }
    fun add(choice: SaleChoice) {
        notice=try { SalesDraftSession.add(choice,quantity.toBigDecimal()) }
        catch(e:Exception){e.message ?: "تعذر إضافة الصنف"}
    }
}
class QuickSaleActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm=ViewModelProvider(this)[QuickSaleViewModel::class.java]
        val catalog=SalesProductCatalog(applicationContext)
        setContent { SalesFrame("فاتورة مبسطة") {
            SalesProductPicker(applicationContext,vm.choices,vm.query,
                {vm.search(it,catalog)},vm.quantity,{vm.quantity=it},{vm.add(it)})
            Text(vm.notice)
            SalesDraftSession.lines.forEach { Text(it.title+" × "+it.quantity.toPlainString()) }
            Button(onClick={startActivity(Intent(this@QuickSaleActivity,SalePaymentsActivity::class.java))}) {
                Text("القبض والحفظ")
            }
        } }
    }
}
