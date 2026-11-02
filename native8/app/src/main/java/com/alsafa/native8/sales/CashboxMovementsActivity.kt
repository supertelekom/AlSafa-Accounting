package com.alsafa.native8.sales

import android.os.Bundle
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
import java.math.BigDecimal

/** Read-only operational ledger viewer. Does not include imported opening balances. */
class CashboxMovementsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var movements by mutableStateOf<List<RoomCashboxMovement>>(emptyList())
        var selectedCashbox by mutableStateOf("شام كاش")
        var selectedCurrency by mutableStateOf("USD")
        lifecycleScope.launch {
            movements = AppDatabase.get(applicationContext).legacyPaymentDao().recentCashboxMovements()
        }
        setContent {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("حركات الصناديق", style = MaterialTheme.typography.headlineSmall)
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
                Text("هذا صافي حركات المبيعات والمرتجعات المعروضة، وليس الرصيد الافتتاحي أو الرصيد الكلي للصندوق.")
                Button(onClick = {
                    lifecycleScope.launch {
                        movements = AppDatabase.get(applicationContext).legacyPaymentDao().recentCashboxMovements()
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
                if (filtered.isEmpty()) Text("لا توجد حركات مسجلة لهذا الصندوق والعملة ضمن آخر 1000 حركة.")
            }
        }
    }
}
