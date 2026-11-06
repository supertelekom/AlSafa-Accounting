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
            if(com.alsafa.native8.stylekit.SafaDateTime.fullMode(this@SalesArchiveActivity)) androidx.compose.foundation.layout.Row {
                listOf("اليوم","هذا الشهر","الشهر السابق","الكل").forEachIndexed { i,label ->
                    TextButton(onClick={val today=java.time.LocalDate.now();vm.fromDate=when(i){0->today.toString();1->today.withDayOfMonth(1).toString();2->today.minusMonths(1).withDayOfMonth(1).toString();else->""};vm.toDate=when(i){2->today.withDayOfMonth(1).minusDays(1).toString();3->"";else->today.toString()};vm.search(vm.query,engine)}) {Text(label)}
                }
            }
            SalesDateInput("من تاريخ", vm.fromDate) { vm.fromDate=it; vm.search(vm.query,engine) }
            SalesDateInput("إلى تاريخ", vm.toDate) { vm.toDate=it; vm.search(vm.query,engine) }
            Button(onClick={ startActivity(Intent(this@SalesArchiveActivity, FullSaleActivity::class.java)) }) { Text("فاتورة مبيع") }
            Button(onClick={ startActivity(Intent(this@SalesArchiveActivity, QuickSaleActivity::class.java)) }) { Text("فاتورة مبسطة") }
            Button(onClick={ startActivity(Intent(this@SalesArchiveActivity, SalesReturnsHistoryActivity::class.java)) }) { Text("المرتجعات القديمة والجديدة") }
            vm.results.forEach { sale -> TextButton(onClick={ startActivity(Intent(this@SalesArchiveActivity, SaleDetailsActivity::class.java).putExtra("sale_id", sale.id)) }) { Text("#${sale.number} | ${when(sale.channel) { "online" -> "بيع أونلاين"; "store" -> "بيع محل"; else -> "نوع البيع غير محدد" }} | ${sale.customer} | ${sale.total}") } }
        } }
    }
}
