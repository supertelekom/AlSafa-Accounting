package com.alsafa.native8.sales

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.alsafa.native8.data.migration.LegacyCashboxHistory
import java.math.BigDecimal
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

/** Unified historical and operational cashbox view with atomic transfers. */
class CashboxMovementsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var movements by mutableStateOf<List<RoomCashboxMovement>>(emptyList())
        var purchaseLabels by mutableStateOf<Map<String,String>>(emptyMap())
        suspend fun refreshPurchaseLabels() {
            val dao=AppDatabase.get(applicationContext).supplierPurchaseDao()
            val suppliers=dao.allSuppliers().associate { it.supplier_id to it.name }
            purchaseLabels=dao.allDrafts().filter { it.status=="POSTED" }.associate {
                it.draft_id to ("شراء من "+(suppliers[it.supplier_id] ?: it.supplier_id)+" — "+it.currency)
            }
        }
        var historical by mutableStateOf(LegacyCashboxHistory.CashHistory(emptyList(), 0, 0))
        var historyError by mutableStateOf("")
        var unified by mutableStateOf<UnifiedCashboxBalance.Result?>(null)
        suspend fun refreshUnified(vault:String,currency:String) {
            try {
                unified=withContext(Dispatchers.IO) {
                    UnifiedCashboxBalance(applicationContext,AppDatabase.get(applicationContext).supplierPurchaseDao()).calculate(vault,currency)
                }
            } catch(e:Exception) { historyError="تعذر حساب الصندوق: "+(e.message ?: "") }
        }
        suspend fun refreshHistory() {
            try {
                historical = withContext(Dispatchers.IO) { LegacyCashboxHistory(applicationContext).cashMovements() }
                historyError = ""
            } catch (e: Exception) { historyError = e.message ?: "تعذر قراءة التاريخ" }
        }
        var transferDestination by mutableStateOf("الصندوق الخارجي")
        var transferAmount by mutableStateOf("")
        var transferNote by mutableStateOf("")
        var transferBusy by mutableStateOf(false)
        var transferStatus by mutableStateOf("")
        var selectedCashbox by mutableStateOf("شام كاش")
        var selectedCurrency by mutableStateOf("USD")
        lifecycleScope.launch {
            movements = AppDatabase.get(applicationContext).legacyPaymentDao().recentCashboxMovements()
            refreshHistory()
            refreshPurchaseLabels()
            refreshUnified(selectedCashbox,selectedCurrency)
        }
        setContent {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("حركات الصناديق", style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = {
                    startActivity(Intent(this@CashboxMovementsActivity, LegacyCashboxHistoryActivity::class.java))
                }) { Text("فحص العمليات التاريخية المستعادة") }
                SalesPaymentSelectors(selectedCashbox, { selectedCashbox = it },
                    selectedCurrency, { selectedCurrency = it })
                Text("تحويل بين الخزن", style = MaterialTheme.typography.titleMedium)
                Text("من: "+selectedCashbox+" — العملة: "+selectedCurrency)
                Text("إلى")
                val destinations=listOf("الدرج","الصندوق الخارجي","شام كاش","البنك")
                destinations.chunked(2).forEach { pair ->
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        pair.forEach { name ->
                            FilterChip(selected=transferDestination==name,
                                onClick={transferDestination=name},label={Text(name)})
                        }
                    }
                }
                OutlinedTextField(value=transferAmount,onValueChange={transferAmount=it},
                    label={Text("المبلغ")},singleLine=true,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                OutlinedTextField(value=transferNote,onValueChange={transferNote=it},
                    label={Text("البيان")},singleLine=true)
                Button(enabled=!transferBusy && transferAmount.toBigDecimalOrNull()?.let { it>BigDecimal.ZERO }==true &&
                    selectedCashbox!=transferDestination,
                    onClick={
                        val source=selectedCashbox
                        val destination=transferDestination
                        val currency=selectedCurrency
                        val amount=transferAmount.toBigDecimalOrNull()
                        if(amount!=null) {
                            transferBusy=true
                            transferStatus="جاري تسجيل التحويل..."
                            lifecycleScope.launch {
                                try {
                                    val result=withContext(Dispatchers.IO) {
                                        UnifiedCashboxOperations(AppDatabase.get(applicationContext))
                                            .transfer(source,destination,currency,amount,transferNote)
                                    }
                                    transferStatus="تم التحويل — المرجع: "+result.reference
                                    transferAmount=""
                                    movements=AppDatabase.get(applicationContext).legacyPaymentDao().recentCashboxMovements()
                                    refreshUnified(selectedCashbox,selectedCurrency)
                                } catch(e:Exception) {
                                    transferStatus="لم ينفذ التحويل: "+(e.message ?: "خطأ غير معروف")
                                } finally { transferBusy=false }
                            }
                        }
                    }) { Text(if(transferBusy) "جاري التحويل..." else "تنفيذ التحويل") }
                if(transferStatus.isNotEmpty()) Text(transferStatus)
                val canonicalCashbox = when (selectedCashbox) {
                    "الدرج" -> "drawer"
                    "الصندوق الخارجي" -> "external"
                    "شام كاش" -> "shamcash"
                    "البنك" -> "bank"
                    else -> selectedCashbox
                }
                val filtered = movements.filter {
                    val normalized = when (it.cashbox.trim().lowercase()) {
                        "الدرج", "drawer" -> "drawer"
                        "الصندوق الخارجي", "external" -> "external"
                        "شام كاش", "shamcash", "sham" -> "shamcash"
                        "البنك", "bank" -> "bank"
                        else -> it.cashbox.trim().lowercase()
                    }
                    normalized == canonicalCashbox && it.currency.equals(selectedCurrency, ignoreCase = true)
                }
                val total = filtered.fold(BigDecimal.ZERO) { sum, movement ->
                    sum + (movement.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO)
                }
                Text("صافي الحركات المسجلة: ${total.toPlainString()} ${selectedCurrency}",
                    style = MaterialTheme.typography.titleLarge)
                val vault = when (selectedCashbox) {
                    "الدرج" -> "drawer"
                    "الصندوق الخارجي" -> "external"
                    "شام كاش" -> "shamcash"
                    "البنك" -> "bank"
                    else -> ""
                }
                val vaultCounts = historical.movements.groupingBy { UnifiedCashboxBalance.displayName(it.vault) }.eachCount()
                Text("الصناديق في النسخة القديمة: " +
                    vaultCounts.entries.sortedBy { it.key }.joinToString(" | ") { "${it.key}: ${it.value}" })
                val legacyRows = historical.movements.filter {
                    UnifiedCashboxBalance.canonical(it.vault)==vault &&
                        it.currency.equals(selectedCurrency, ignoreCase = true)
                }
                val legacyTotal = legacyRows.fold(BigDecimal.ZERO) { sum, item -> sum + item.amount }
                Text("صافي الحركات التاريخية: ${legacyTotal.toPlainString()} ${selectedCurrency}")

                LaunchedEffect(selectedCashbox,selectedCurrency) {
                    refreshUnified(selectedCashbox,selectedCurrency)
                }
                unified?.let { balance ->
                    Text("الرصيد الموحد — "+UnifiedCashboxBalance.displayName(selectedCashbox)+": "+
                        balance.total.toPlainString()+" "+selectedCurrency,
                        style = MaterialTheme.typography.headlineSmall)
                    Text("رصيد مستعاد: "+balance.historical.toPlainString()+
                        " | حركات جديدة: "+balance.operational.toPlainString())
                    Text(balance.warning)
                }
                Text("يُستخدم الرصيد الموحد في العمليات التي اجتازت فحوص المطابقة الآلية؛ التدقيق المحاسبي النهائي مؤجل.")
                Text("سجلات تاريخية مقروءة: ${historical.movements.size} — غير صالحة: ${historical.invalid} — معرفات مكررة: ${historical.duplicates}")
                if (historyError.isNotEmpty()) Text(historyError)
                Button(onClick = {
                    lifecycleScope.launch {
                        movements = AppDatabase.get(applicationContext).legacyPaymentDao().recentCashboxMovements()
                        refreshHistory()
                        refreshPurchaseLabels()
                        refreshUnified(selectedCashbox,selectedCurrency)
                    }
                }) { Text("تحديث") }
                filtered.forEach { movement ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${movement.amount} ${movement.currency}")
                            Text(purchaseLabels[movement.sale_id] ?: movement.note)
                            if (!purchaseLabels.containsKey(movement.sale_id)) Text("المرجع: ${movement.sale_id.take(8)}")
                        }
                    }
                }
                if (filtered.isEmpty()) Text("لا توجد حركات جديدة لهذا الصندوق والعملة ضمن آخر 1000 حركة.")
                Text("الحركات التاريخية", style = MaterialTheme.typography.titleMedium)
                legacyRows.asReversed().take(150).forEach { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${item.amount.toPlainString()} ${item.currency} — ${item.type}")
                            Text("${item.date} — ${item.id}")
                            if (item.referenceId.isNotBlank()) Text("المرجع: ${item.referenceType} / ${item.referenceId}")
                        }
                    }
                }
                if (legacyRows.isEmpty()) Text("لا توجد حركات تاريخية لهذا الصندوق والعملة.")
            }
        }
    }
}
