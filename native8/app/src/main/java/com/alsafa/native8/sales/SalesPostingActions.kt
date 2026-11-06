package com.alsafa.native8.sales

import android.content.Context

/** Single entry point for both invoice modes. */
class SalesPostingActions(context: Context) {
    private val engine = SalesEngine(context.applicationContext)
    private val appContext = context.applicationContext
    private val rates = ExchangeRateSettings(appContext)
    private fun snapshot(cashbox: String, currency: String, amount: String): SalePayment {
        val value = amount.toBigDecimal()
        require(value > java.math.BigDecimal.ZERO)
        val rate = rates.rate(currency, "USD")
        val credited = value.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP)
        return SalePayment(cashbox, currency, value, "USD", rate, credited)
    }
    fun postDraft(amount: String = "", cashbox: String = "", currency: String = ""): String {
        val extra = if(amount.isBlank()) null else snapshot(cashbox,currency,amount)
        val draft = SalesDraftSession.draft()
        val id = engine.save(draft.copy(payments = draft.payments + listOfNotNull(extra)))
        SalesDraftSession.clear()
        return id
    }
    fun collect(saleId: String, amount: String, cashbox: String, currency: String): String = kotlinx.coroutines.runBlocking {
        if(AppDatabase.get(appContext).salesPostingDao().sale(saleId)!=null)
            RoomSalesEngine(appContext).collect(saleId,snapshot(cashbox,currency,amount))
        else SalesRoomRepository(appContext).collectConfirmedHistorical(saleId,cashbox,currency,amount.toBigDecimal())
    }
}
