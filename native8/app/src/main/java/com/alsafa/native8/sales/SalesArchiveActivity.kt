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
    var query by mutableStateOf(""); private set
    var results by mutableStateOf<List<SaleRecord>>(emptyList()); private set
    fun search(value: String, engine: SalesEngine) { query=value; results=engine.archive(value) }
}

class SalesArchiveActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this)[SalesArchiveViewModel::class.java]
        
        val engine = SalesEngine(applicationContext)
        vm.search("", engine)
        setContent { SalesFrame("فواتير المبيع") {
            SalesInput("بحث بالاسم أو الهاتف أو رقم الفاتورة", vm.query) { vm.search(it, engine) }
            Button(onClick={ startActivity(Intent(this@SalesArchiveActivity, FullSaleActivity::class.java)) }) { Text("فاتورة مبيع") }
            Button(onClick={ startActivity(Intent(this@SalesArchiveActivity, QuickSaleActivity::class.java)) }) { Text("فاتورة مبسطة") }
            vm.results.forEach { sale -> TextButton(onClick={ startActivity(Intent(this@SalesArchiveActivity, SaleDetailsActivity::class.java).putExtra("sale_id", sale.id)) }) { Text("#${sale.number} | ${sale.customer} | ${sale.total}") } }
        } }
    }
}
