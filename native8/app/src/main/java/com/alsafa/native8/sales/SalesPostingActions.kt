package com.alsafa.native8.sales

import android.content.Context

/** Single entry point for both invoice modes. */
class SalesPostingActions(context: Context) {
    private val engine = SalesEngine(context.applicationContext)
    fun postDraft(amount: String = "", cashbox: String = "", currency: String = ""): String {
        if (amount.isNotBlank()) SalesDraftSession.payments.add(SalePayment(cashbox,currency,amount.toBigDecimal()))
        val id = engine.save(SalesDraftSession.draft())
        SalesDraftSession.clear()
        return id
    }
    fun collect(saleId: String, amount: String, cashbox: String, currency: String): String =
        engine.collect(saleId, SalePayment(cashbox, currency, amount.toBigDecimal()))
}
