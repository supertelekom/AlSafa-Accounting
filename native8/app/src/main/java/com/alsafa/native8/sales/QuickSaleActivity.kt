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

class QuickSaleViewModel: ViewModel() {
    var query by mutableStateOf("")
    var quantity by mutableStateOf("1")
    var choices by mutableStateOf<List<SaleChoice>>(emptyList())
    var notice by mutableStateOf("")
    private var searchJob: Job? = null
    fun search(value: String, catalog: SalesProductCatalog) {
        query=value
        searchJob?.cancel()
        choices=emptyList()
        if(value.isBlank()) return
        searchJob=viewModelScope.launch {
            delay(250)
            try { choices=withContext(Dispatchers.IO) { catalog.find(value) } }
            catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; notice=e.message ?: "تعذر البحث" }
        }
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
