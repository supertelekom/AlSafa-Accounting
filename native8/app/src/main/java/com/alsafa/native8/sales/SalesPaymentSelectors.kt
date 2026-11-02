package com.alsafa.native8.sales

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/** Fixed operational cashboxes and currencies; avoid arbitrary text in the ledger. */
internal object SalesPaymentOptions {
    val cashboxes = listOf("الدرج", "الصندوق الخارجي", "شام كاش", "البنك")
    val currencies = listOf("USD", "TRY", "SYP")
}

@Composable
fun SalesPaymentSelectors(
    cashbox: String, onCashbox: (String) -> Unit,
    currency: String, onCurrency: (String) -> Unit
) {
    Text("الصندوق")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SalesPaymentOptions.cashboxes.forEachIndexed { index, option ->
            SegmentedButton(
                selected = cashbox == option,
                onClick = { onCashbox(option) },
                shape = SegmentedButtonDefaults.itemShape(index, SalesPaymentOptions.cashboxes.size)
            ) { Text(option) }
        }
    }
    Text("العملة")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SalesPaymentOptions.currencies.forEachIndexed { index, option ->
            SegmentedButton(
                selected = currency == option,
                onClick = { onCurrency(option) },
                shape = SegmentedButtonDefaults.itemShape(index, SalesPaymentOptions.currencies.size)
            ) { Text(option) }
        }
    }
}
