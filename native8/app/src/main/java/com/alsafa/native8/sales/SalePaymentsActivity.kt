package com.alsafa.native8.sales
import android.os.Bundle
import android.content.Intent
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
    val entered = (vm.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) * ExchangeRateSettings(applicationContext).rate(vm.currency, "USD")
    Text("المتبقي بعد الدفعة (USD): " + (vm.remaining!!.toBigDecimal() - entered).max(BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
     style = MaterialTheme.typography.titleLarge)
   }
   SalesPaymentSelectors(vm.cashbox,{vm.cashbox=it},vm.currency,{vm.currency=it})
   val exchange = ExchangeRateSettings(applicationContext).rate(vm.currency, "USD")
   val quoted = ExchangeRateSettings(applicationContext).rate("USD", vm.currency)
   Text(if(vm.currency == "USD") "1 USD = 1 USD" else "1 USD = " + quoted.stripTrailingZeros().toPlainString() + " " + vm.currency)
   Text("المبلغ المدخل: " + (vm.amount.ifBlank { "0" }) + " " + vm.currency)
   Text("القيمة المحتسبة بالدولار: " + ((vm.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) * exchange).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
   Text("الفاتورة: "+vm.saleId)
   Button(enabled=vm.saleId.isNotBlank() && !vm.posting && (vm.remaining?.toBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO,onClick={
    try {
     require(vm.saleId.isNotBlank()) { "اختر فاتورة محفوظة" }
     vm.posting=true
     val id=SalesPostingActions(applicationContext).collect(vm.saleId,vm.amount,vm.cashbox,vm.currency)
     vm.status="تم تسجيل القبض: "+id
     startActivity(Intent(this@SalePaymentsActivity, SalesArchiveActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
     })
     finish()
    } catch(e:Exception) {vm.status=e.message ?: "تعذر القبض"}
    finally { vm.posting=false }
   }) {Text("تسجيل دفعة")}
   if(vm.saleId.isNotBlank() && vm.remaining?.toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO) == 0) Text("الفاتورة مسددة بالكامل؛ لا توجد دفعة إضافية")
   Text(vm.status)
   if(vm.saleId.isBlank()) Button(enabled=!vm.posting,onClick={
     vm.posting=true
     try { vm.saleId=SalesPostingActions(applicationContext).postDraft(vm.amount,vm.cashbox,vm.currency); vm.status="تم حفظ الفاتورة"
      startActivity(Intent(this@SalePaymentsActivity, SalesArchiveActivity::class.java).apply {
       addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      })
      finish() }
     catch(e:Exception){ vm.status=e.message ?: "تعذر حفظ الفاتورة" }
     finally { vm.posting=false }
   }) { Text("حفظ الفاتورة") }
  } }
 }
}