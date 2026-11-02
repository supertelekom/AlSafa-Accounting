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

/** Read-only operational ledger viewer. Does not include imported opening balances. */
class CashboxMovementsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var movements by mutableStateOf<List<RoomCashboxMovement>>(emptyList())
        var historical by mutableStateOf(LegacyCashboxHistory.CashHistory(emptyList(), 0, 0))
        var historyError by mutableStateOf("")
        suspend fun refreshHistory() {
            try {
                historical = withContext(Dispatchers.IO) { LegacyCashboxHistory(applicationContext).cashMovements() }
                historyError = ""
            } catch (e: Exception) { historyError = e.message ?: "تعذر قراءة التاريخ" }
        }
        var selectedCashbox by mutableStateOf("شام كاش")
        var selectedCurrency by mutableStateOf("USD")
        lifecycleScope.launch {
            movements = AppDatabase.get(applicationContext).legacyPaymentDao().recentCashboxMovements()
            refreshHistory()
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
                val filtered = movements.filter {
                    it.cashbox == selectedCashbox && it.currency == selectedCurrency
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
                val vaultCounts = historical.movements.groupingBy { it.vault }.eachCount()
                Text("معرفات الصناديق في النسخة القديمة: " +
                    vaultCounts.entries.sortedBy { it.key }.joinToString(" | ") { "${it.key}: ${it.value}" })
                val legacyRows = historical.movements.filter {
                    (it.vault.equals(vault, ignoreCase = true) ||
                        (vault == "shamcash" && it.vault.equals("sham", ignoreCase = true))) &&
                        it.currency.equals(selectedCurrency, ignoreCase = true)
                }
                val legacyTotal = legacyRows.fold(BigDecimal.ZERO) { sum, item -> sum + item.amount }
                Text("صافي الحركات التاريخية: ${legacyTotal.toPlainString()} ${selectedCurrency}")
                Text("مجموع الحركات القديمة والجديدة: ${(total + legacyTotal).toPlainString()} ${selectedCurrency}",
                    style = MaterialTheme.typography.titleLarge)
                Text("هذه مجاميع حركات، وليست رصيد الصندوق النهائي؛ الأرصدة الافتتاحية والإغلاقات قيد المطابقة.")
                Text("سجلات تاريخية مقروءة: ${historical.movements.size} — غير صالحة: ${historical.invalid} — معرفات مكررة: ${historical.duplicates}")
                if (historyError.isNotEmpty()) Text(historyError)
                Button(onClick = {
                    lifecycleScope.launch {
                        movements = AppDatabase.get(applicationContext).legacyPaymentDao().recentCashboxMovements()
                        refreshHistory()
                    }
                }) { Text("تحديث") }
                filtered.forEach { movement ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${movement.amount} ${movement.currency}")
                            Text(movement.note)
                            Text("الفاتورة: ${movement.sale_id}")
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
