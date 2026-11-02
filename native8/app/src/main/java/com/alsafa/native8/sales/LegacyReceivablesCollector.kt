package com.alsafa.native8.sales

import android.content.Context
import kotlinx.coroutines.runBlocking

/** Historical collections share AppDatabase and its Room transaction. */
class LegacyReceivablesCollector(context: Context) {
 private val repository=SalesRoomRepository(context.applicationContext)
 fun collect(id: String, payment: SalePayment): String = runBlocking {
  repository.collectConfirmedHistorical(id,payment.cashbox,payment.currency,payment.amount)
 }
}
