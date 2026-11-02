package com.alsafa.native8.sales

import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.math.BigDecimal
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** System print / PDF export. Does not post or alter any accounting records. */
class SalePrintActivity : ComponentActivity() {
    private var printView: WebView? = null
    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onDestroy() {
        printView?.destroy()
        printView = null
        super.onDestroy()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saleId = intent.getStringExtra("sale_id").orEmpty()
        var html by mutableStateOf("")
        var plain by mutableStateOf("")
        var error by mutableStateOf("")
        var loading by mutableStateOf(true)
        var devices by mutableStateOf<List<ThermalBluetoothPrinter.PairedPrinter>>(emptyList())
        var selectedPrinter by mutableStateOf("")
        var width by mutableStateOf(80)
        lifecycleScope.launch {
            try {
                val db = AppDatabase.get(applicationContext)
                val current = db.salesPostingDao().sale(saleId)
                val legacy = if (current == null) db.legacySaleDao().legacyById(saleId) else null
                val lines = RoomSalesEngine(applicationContext).lines(saleId)
                if (current == null && legacy == null) error = "الفاتورة غير موجودة"
                else {
                    val number = current?.number?.toString() ?: legacy!!.invoice_number.toString()
                    val customer = current?.customer ?: legacy!!.customer
                    val total = current?.total ?: legacy!!.total
                    val paid = current?.paid ?: legacy!!.historical_paid
                    val rows = lines.joinToString("") { line ->
                        "<tr><td>${escape(line.title)}</td><td>${escape(line.quantity)}</td><td>${escape(line.price)}</td><td>${escape(line.returned)}</td></tr>"
                    }
                    html = """<!doctype html><html dir="rtl"><head><meta charset="utf-8"><style>
                    body{font-family:sans-serif;direction:rtl;margin:16px;color:#111}
                    h2{text-align:center}table{width:100%;border-collapse:collapse}
                    td,th{border-bottom:1px solid #ddd;padding:7px;text-align:right}
                    @media print{body{margin:4mm}}
                    </style></head><body><h2>الصفا للساعات</h2>
                    <p>فاتورة رقم: ${escape(number)}</p><p>العميل: ${escape(customer)}</p>
                    <table><tr><th>الصنف</th><th>الكمية</th><th>السعر</th><th>المرتجع</th></tr>$rows</table>
                    <p>الإجمالي: ${escape(total)}</p><p>المقبوض: ${escape(paid)}</p>
                    </body></html>"""
                    plain = "الصفا للساعات\nفاتورة رقم: $number\nالعميل: $customer\n" +
                        lines.joinToString("\n") { "${it.title} × ${it.quantity} — ${it.price}" } +
                        "\nالإجمالي: $total\nالمقبوض: $paid"
                }
            } catch (e: Exception) { error = e.message ?: "تعذر تحميل الفاتورة" }
            finally { loading = false }
        }
        setContent {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("طباعة ومشاركة الفاتورة", style = MaterialTheme.typography.headlineSmall)
                if (loading) CircularProgressIndicator()
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                if (html.isNotBlank()) {
                    Text("الفاتورة جاهزة للطباعة عبر نظام أندرويد أو الحفظ PDF.")
                    Button(onClick = {
                        val web = WebView(this@SalePrintActivity)
                        printView?.destroy()
                        printView = web
                        web.settings.defaultTextEncodingName = "UTF-8"
                        web.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (view !== printView) return
                                val manager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                                manager.print("AlSafa-Invoice", web.createPrintDocumentAdapter("AlSafa-Invoice"),
                                    PrintAttributes.Builder().build())
                            }
                        }
                        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    }) { Text("طباعة / حفظ PDF") }
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, plain)
                            setPackage("com.whatsapp.w4b")
                        }
                        try { startActivity(intent) }
                        catch (e: android.content.ActivityNotFoundException) {
                            error = "واتساب Business غير مثبت أو غير متاح"
                        }
                    }) { Text("مشاركة نص الفاتورة عبر واتساب Business") }
                    HorizontalDivider()
                    Text("طابعة حرارية مقترنة", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 31 &&
                            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        } else {
                            try { devices = ThermalBluetoothPrinter(applicationContext).paired() }
                            catch (e: Exception) { error = e.message ?: "تعذر قراءة الطابعات" }
                        }
                    }) { Text("عرض الطابعات المقترنة") }
                    devices.forEach { printer ->
                        OutlinedButton(onClick = { selectedPrinter = printer.address }) {
                            Text((if (selectedPrinter == printer.address) "✓ " else "") + printer.name)
                        }
                    }
                    Row {
                        listOf(58, 80).forEach { size ->
                            OutlinedButton(onClick = { width = size }) {
                                Text((if (width == size) "✓ " else "") + "$size مم")
                            }
                        }
                    }
                    Button(enabled = selectedPrinter.isNotBlank(), onClick = {
                        lifecycleScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    ThermalBluetoothPrinter(applicationContext).print(selectedPrinter, plain, width)
                                }
                                error = "أُرسلت بيانات الطباعة للطابعة"
                            } catch (e: Exception) { error = e.message ?: "فشل الاتصال بالطابعة" }
                        }
                    }) { Text("طباعة بلوتوث تجريبية") }
                    Text("الطباعة المباشرة الحالية نصية تجريبية؛ دعم العربية وقالب الصفا الأصلي وإرسال PDF يحتاج اختبار الطابعة وربط القالب.")
                }
            }
        }
    }
    private fun escape(value: String): String = value.replace("&", "&amp;")
        .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
