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

class SalesArchiveViewModel: ViewModel() {
    var fromDate by mutableStateOf("")
    var toDate by mutableStateOf("")
    var query by mutableStateOf(""); private set
    var results by mutableStateOf<List<SaleRecord>>(emptyList()); private set
    fun search(value: String, engine: SalesEngine) {
        query=value
        val from = runCatching { java.time.LocalDate.parse(fromDate).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
        val to = runCatching { java.time.LocalDate.parse(toDate).plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()-1 }.getOrNull()
        results=engine.archive(value,from,to)
    }
}

class SalesArchiveActivity: ComponentActivity() {
    private lateinit var archiveVm: SalesArchiveViewModel
    override fun onResume() {
        super.onResume()
        if (::archiveVm.isInitialized) archiveVm.search(archiveVm.query, SalesEngine(applicationContext))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this)[SalesArchiveViewModel::class.java]
        archiveVm = vm
        
        val engine = SalesEngine(applicationContext)
        vm.search("", engine)
        setContent { SalesFrame("فواتير المبيع") {
            SalesInput("بحث بالاسم أو الهاتف أو رقم الفاتورة", vm.query) { vm.search(it, engine) }
            SalesInput("من تاريخ YYYY-MM-DD", vm.fromDate) { vm.fromDate=it; vm.search(vm.query,engine) }
            SalesInput("إلى تاريخ YYYY-MM-DD", vm.toDate) { vm.toDate=it; vm.search(vm.query,engine) }
            Button(onClick={ startActivity(Intent(this@SalesArchiveActivity, FullSaleActivity::class.java)) }) { Text("فاتورة مبيع") }
            Button(onClick={ startActivity(Intent(this@SalesArchiveActivity, QuickSaleActivity::class.java)) }) { Text("فاتورة مبسطة") }
            vm.results.forEach { sale -> TextButton(onClick={ startActivity(Intent(this@SalesArchiveActivity, SaleDetailsActivity::class.java).putExtra("sale_id", sale.id)) }) { Text("#${sale.number} | ${when(sale.channel) { "online" -> "بيع أونلاين"; "store" -> "بيع محل"; else -> "نوع البيع غير محدد" }} | ${sale.customer} | ${sale.total}") } }
        } }
    }
}
