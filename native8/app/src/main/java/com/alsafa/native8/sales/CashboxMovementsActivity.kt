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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
        var exchangeTo by mutableStateOf("USD")
        var exchangeOutgoing by mutableStateOf("")
        var exchangeIncoming by mutableStateOf("")
        var exchangeStatus by mutableStateOf("")
        var exchangeBusy by mutableStateOf(false)
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
                Button(onClick={startActivity(Intent(this@CashboxMovementsActivity,DailyClosingActivity::class.java))}) {Text("الإغلاق اليومي")}
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
                                    transferStatus="تم التحويل بنجاح"
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
                Text("صرف عملة",style=MaterialTheme.typography.titleMedium)
                Text("من "+selectedCurrency+" في "+selectedCashbox+" إلى:")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("USD","TRY","SYP").filter {
                        it!=selectedCurrency && (selectedCashbox!="شام كاش" || it!="TRY")
                    }.forEach { unit ->
                        FilterChip(selected=exchangeTo==unit,onClick={exchangeTo=unit},label={Text(unit)})
                    }
                }
                OutlinedTextField(value=exchangeOutgoing,onValueChange={exchangeOutgoing=it},
                    label={Text("المبلغ الخارج — "+selectedCurrency)},singleLine=true,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                OutlinedTextField(value=exchangeIncoming,onValueChange={exchangeIncoming=it},
                    label={Text("المبلغ الداخل — "+exchangeTo)},singleLine=true,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                Button(enabled=!exchangeBusy && exchangeTo!=selectedCurrency &&
                    exchangeOutgoing.toBigDecimalOrNull()?.let {it>BigDecimal.ZERO}==true &&
                    exchangeIncoming.toBigDecimalOrNull()?.let {it>BigDecimal.ZERO}==true,
                    onClick={
                        val from=selectedCurrency
                        val to=exchangeTo
                        val vault=selectedCashbox
                        val outgoing=exchangeOutgoing.toBigDecimalOrNull()
                        val incoming=exchangeIncoming.toBigDecimalOrNull()
                        if(outgoing!=null && incoming!=null) {
                            exchangeBusy=true
                            lifecycleScope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        UnifiedCashboxOperations(AppDatabase.get(applicationContext))
                                            .exchange(vault,from,to,outgoing,incoming)
                                    }
                                    exchangeStatus="تم صرف العملة بنجاح"
                                    exchangeOutgoing=""
                                    exchangeIncoming=""
                                    movements=AppDatabase.get(applicationContext).legacyPaymentDao().recentCashboxMovements()
                                    refreshUnified(selectedCashbox,selectedCurrency)
                                } catch(e:Exception) {
                                    exchangeStatus="لم ينفذ الصرف: "+(e.message ?: "خطأ غير معروف")
                                } finally {exchangeBusy=false}
                            }
                        }
                    }) {Text("تنفيذ صرف العملة")}
                if(exchangeStatus.isNotEmpty()) Text(exchangeStatus)

                val canonicalCashbox = UnifiedCashboxBalance.canonical(selectedCashbox) ?: selectedCashbox
                val filtered = movements.filter {
                    val normalized = UnifiedCashboxBalance.canonical(it.cashbox)
                    normalized == canonicalCashbox && it.currency.equals(selectedCurrency, ignoreCase = true)
                }
                val vault = canonicalCashbox
                val legacyRows = historical.movements.filter {
                    UnifiedCashboxBalance.canonical(it.vault)==vault &&
                        it.currency.equals(selectedCurrency, ignoreCase = true)
                }
                LaunchedEffect(selectedCashbox,selectedCurrency) {
                    refreshUnified(selectedCashbox,selectedCurrency)
                }
                unified?.let { balance ->
                    Text("الرصيد الموحد — "+UnifiedCashboxBalance.displayName(selectedCashbox)+": "+
                        balance.total.toPlainString()+" "+selectedCurrency,
                        style = MaterialTheme.typography.headlineSmall)
                    Text(balance.warning)
                }
                if (historyError.isNotEmpty()) Text(historyError)
                Button(onClick = {
                    lifecycleScope.launch {
                        movements = AppDatabase.get(applicationContext).legacyPaymentDao().recentCashboxMovements()
                        refreshHistory()
                        refreshPurchaseLabels()
                        refreshUnified(selectedCashbox,selectedCurrency)
                    }
                }) { Text("تحديث") }
                Text("حركات "+selectedCashbox, style=MaterialTheme.typography.titleMedium)
                val formatter=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault())
                // One chronological view; historical records stay read-only and are not reposted.
                val combined=buildList {
                    filtered.forEach { movement ->
                        add(Triple(movement.created_at,
                            movement.amount+" "+movement.currency,
                            CashboxMovementLabels.operational(purchaseLabels[movement.sale_id] ?: movement.note,movement.sale_id)+
                                " — "+formatter.format(Instant.ofEpochMilli(movement.created_at))))
                    }
                    legacyRows.forEach { item ->
                        val parsed=runCatching {
                            java.time.LocalDate.parse(item.date.take(10))
                                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }.getOrDefault(0L)
                        add(Triple(parsed,item.amount.toPlainString()+" "+item.currency,
                            CashboxMovementLabels.type(item.type)+" — "+item.date)))
                    }
                }.sortedByDescending { it.first }
                combined.forEach { (_,amount,description) ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(amount,style=MaterialTheme.typography.titleMedium)
                            Text(description)
                        }
                    }
                }
                if(combined.isEmpty()) Text("لا توجد حركات لهذا الصندوق والعملة.")
            }
        }
    }
}
