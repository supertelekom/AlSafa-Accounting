package com.alsafa.native8.sales

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FullSaleViewModel: ViewModel() {
    var search by mutableStateOf("")
    var quantity by mutableStateOf("1")
    var choices by mutableStateOf<List<SaleChoice>>(emptyList())
    var notice by mutableStateOf("")
    private var searchJob: Job? = null
    fun searchProducts(value: String, catalog: SalesProductCatalog) {
        search = value
        searchJob?.cancel()
        choices = emptyList()
        if (value.isBlank()) return
        searchJob = viewModelScope.launch {
            delay(250)
            try { choices = withContext(Dispatchers.IO) { catalog.find(value) } }
            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; notice = e.message ?: "تعذر البحث" }
        }
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
            Text("نوع البيع *")
            Row {
                RadioButton(
                    selected=SalesDraftSession.channel==SalesChannelClassification.STORE,
                    onClick={SalesDraftSession.channel=SalesChannelClassification.STORE}
                )
                Text("بيع من المحل")
                RadioButton(
                    selected=SalesDraftSession.channel==SalesChannelClassification.ONLINE,
                    onClick={SalesDraftSession.channel=SalesChannelClassification.ONLINE}
                )
                Text("بيع أونلاين")
            }
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
