package com.alsafa.native8.sales

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class FullSaleViewModel: ViewModel() {
    var search by mutableStateOf("")
    var quantity by mutableStateOf("1")
    var choices by mutableStateOf<List<SaleChoice>>(emptyList())
    var notice by mutableStateOf("")
    fun searchProducts(value: String, catalog: SalesProductCatalog) {
        search = value
        choices = catalog.find(value)
    }
    fun add(choice: SaleChoice) {
        notice = try { SalesDraftSession.add(choice, quantity.toBigDecimal()) }
        catch (e: Exception) { e.message ?: "تعذر إضافة الصنف" }
    }
}
class FullSaleActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this)[FullSaleViewModel::class.java]
        val catalog = SalesProductCatalog(applicationContext)
        setContent { SalesFrame("فاتورة مبيع") {
            SalesInput("اسم العميل", SalesDraftSession.customer) { SalesDraftSession.customer=it }
            SalesInput("رقم الهاتف", SalesDraftSession.phone) { SalesDraftSession.phone=it }
            SalesProductPicker(applicationContext,vm.choices,vm.search,
                { vm.searchProducts(it,catalog) },vm.quantity,{vm.quantity=it},{vm.add(it)})
            Text(vm.notice)
            SalesInput("الخصم",SalesDraftSession.discount){SalesDraftSession.discount=it}
            SalesDraftSession.lines.forEach { line ->
                Text(line.title+" × "+line.quantity.toPlainString()+" | "+line.unitPrice.toPlainString())
            }
            Button(onClick={startActivity(Intent(this@FullSaleActivity,SalePaymentsActivity::class.java))}) {
                Text("القبض والحفظ")
            }
        } }
    }
}
