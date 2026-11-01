package com.alsafa.native8.sales

import android.content.Context

/** Single entry point for both invoice modes. */
class SalesPostingActions(context: Context) {
    private val engine = SalesEngine(context.applicationContext)
    private val appContext = context.applicationContext
    fun postDraft(amount: String = "", cashbox: String = "", currency: String = ""): String {
        val extra = if(amount.isBlank()) null else SalePayment(cashbox,currency,amount.toBigDecimal())
        val draft = SalesDraftSession.draft()
        val id = engine.save(draft.copy(payments = draft.payments + listOfNotNull(extra)))
        SalesDraftSession.clear()
        return id
    }
    fun collect(saleId: String, amount: String, cashbox: String, currency: String): String =
        if (saleId.startsWith("sale-")) LegacyReceivablesCollector(appContext).collect(saleId,SalePayment(cashbox,currency,amount.toBigDecimal()))
        else engine.collect(saleId,SalePayment(cashbox,currency,amount.toBigDecimal()))
}
