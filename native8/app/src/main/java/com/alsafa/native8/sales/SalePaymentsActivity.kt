package com.alsafa.native8.sales
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.math.BigDecimal
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
class SalePaymentsViewModel: ViewModel() {
 var amount by mutableStateOf("")
 var cashbox by mutableStateOf("الدرج")
 var currency by mutableStateOf("USD")
 var saleId by mutableStateOf("")
 var status by mutableStateOf("")
 var posting by mutableStateOf(false)
 var remaining by mutableStateOf<String?>(null)
 fun refreshBalance(context: android.content.Context) {
  if (saleId.isBlank() || saleId.startsWith("sale-")) return
  viewModelScope.launch {
   remaining = AppDatabase.get(context).legacyReceivablesDao().bySaleId(saleId)?.balance ?: "0"
  }
 }
}
class SalePaymentsActivity: ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val vm=ViewModelProvider(this)[SalePaymentsViewModel::class.java]
  vm.saleId=intent.getStringExtra("sale_id").orEmpty()
  vm.refreshBalance(applicationContext)
  setContent { SalesFrame("القبض والدفعات") {
   if (vm.saleId.isBlank()) {
    val draft = runCatching { SalesDraftSession.draft() }.getOrNull()
    if (draft != null && draft.lines.isNotEmpty()) {
     val total = draft.lines.fold(BigDecimal.ZERO) { sum, line -> sum + line.quantity * line.unitPrice }
     val discount = draft.discount + draft.lines.fold(BigDecimal.ZERO) { sum, line -> sum + line.discount }
     val net = total - discount
     val alreadyPaid = draft.payments.fold(BigDecimal.ZERO) { sum, payment -> sum + (payment.creditedAmount ?: BigDecimal.ZERO) }
     val current = (vm.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) * ExchangeRateSettings(applicationContext).rate(vm.currency, "USD")
     Text("الإجمالي: " + total.toPlainString())
     Text("الخصم: " + discount.toPlainString())
     Text("الصافي: " + net.toPlainString())
     Text("المدفوع: " + (alreadyPaid + current).toPlainString())
     Text("المتبقي: " + (net - alreadyPaid - current).max(BigDecimal.ZERO).toPlainString(),
      style = MaterialTheme.typography.titleLarge)
    }
   }
   SalesInput("المبلغ",vm.amount){vm.amount=it}
   if (vm.saleId.isNotBlank() && vm.remaining != null) {
    Text("المتبقي قبل الدفعة: " + vm.remaining)
    val entered = vm.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
    Text("المتبقي بعد الدفعة: " + (vm.remaining!!.toBigDecimal() - entered).max(BigDecimal.ZERO).toPlainString(),
     style = MaterialTheme.typography.titleLarge)
   }
   SalesPaymentSelectors(vm.cashbox,{vm.cashbox=it},vm.currency,{vm.currency=it})
   val exchange = ExchangeRateSettings(applicationContext).rate(vm.currency, "USD")
   Text("سعر الصرف إلى الدولار: " + exchange.stripTrailingZeros().toPlainString())
   Text("القيمة المحتسبة بالدولار: " + ((vm.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) * exchange).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
   Text("الفاتورة: "+vm.saleId)
   Button(onClick={
    try {
     require(vm.saleId.isNotBlank()) { "اختر فاتورة محفوظة" }
     val id=SalesPostingActions(applicationContext).collect(vm.saleId,vm.amount,vm.cashbox,vm.currency)
     vm.status="تم تسجيل القبض: "+id
     vm.refreshBalance(applicationContext)
    } catch(e:Exception) {vm.status=e.message ?: "تعذر القبض"}
   }) {Text("تسجيل دفعة")}
   Text(vm.status)
   if(vm.saleId.isBlank()) Button(enabled=!vm.posting,onClick={
     vm.posting=true
     try { vm.saleId=SalesPostingActions(applicationContext).postDraft(vm.amount,vm.cashbox,vm.currency); vm.status="تم حفظ الفاتورة"; vm.refreshBalance(applicationContext) }
     catch(e:Exception){ vm.status=e.message ?: "تعذر حفظ الفاتورة" }
     finally { vm.posting=false }
   }) { Text("حفظ الفاتورة") }
  } }
 }
}