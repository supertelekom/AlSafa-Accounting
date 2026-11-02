package com.alsafa.native8.sales

import android.content.Context
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal

/** Shared financial calculation and persistence for full and quick sales. */
class SalesEngine(private val context: Context) {
    fun calculate(draft: SaleDraft): SaleTotals {
        require(draft.lines.isNotEmpty()) { "الفاتورة فارغة" }
        require(draft.lines.all { it.quantity > BigDecimal.ZERO && it.unitPrice >= BigDecimal.ZERO && it.discount >= BigDecimal.ZERO })
        val gross = draft.lines.fold(BigDecimal.ZERO) { a, l -> a + l.quantity * l.unitPrice }
        val discount = draft.discount + draft.lines.fold(BigDecimal.ZERO) { a, l -> a + l.discount }
        require(discount >= BigDecimal.ZERO && discount <= gross) { "الخصم غير صالح" }
        val net = gross - discount
        val currencies = draft.payments.map { it.currency }.distinct()
        require(currencies.size <= 1) { "القبض بعملات مختلفة يحتاج أسعار صرف معتمدة" }
        require(draft.payments.all { it.amount > BigDecimal.ZERO && it.cashbox.isNotBlank() })
        val paid = draft.payments.fold(BigDecimal.ZERO) { a, p -> a + p.amount }
        return SaleTotals(gross, discount, net, paid, (net - paid).max(BigDecimal.ZERO), (paid - net).max(BigDecimal.ZERO))
    }

    /** Preserve the existing synchronous screen API; all database work is Room-backed. */
    fun save(draft: SaleDraft): String = runBlocking {
        RoomSalesEngine(context).save(draft, calculate(draft))
    }

    fun collect(saleId: String, payment: SalePayment): String = runBlocking {
        RoomSalesEngine(context).collect(saleId, payment)
    }

    fun returnLine(saleId: String, lineId: String, quantity: BigDecimal, reason: String,
        refundCashbox: String = "", refundCurrency: String = ""): String =
        runBlocking { RoomSalesEngine(context).returnLine(saleId, lineId, quantity, reason, refundCashbox, refundCurrency) }

    data class InvoiceLine(val id: String,val title: String,val quantity: String,val returned: String,val price: String)
    fun lines(saleId: String): List<InvoiceLine> = runBlocking {
        RoomSalesEngine(context).lines(saleId)
    }

    fun archive(search: String = "", from: Long? = null, to: Long? = null): List<SaleRecord> =
        runBlocking { RoomSalesEngine(context).archive(search, from, to) }
}
