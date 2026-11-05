package com.alsafa.native8.sales

import java.math.BigDecimal

data class SaleLine(val productSource: String, val productId: String, val variantId: String? = null,
    val title: String, val quantity: BigDecimal, val unitPrice: BigDecimal, val unitCost: BigDecimal,
    val discount: BigDecimal = BigDecimal.ZERO)
data class SalePayment(val cashbox: String, val currency: String, val amount: BigDecimal,
    val invoiceCurrency: String? = null, val fxRate: BigDecimal? = null,
    val creditedAmount: BigDecimal? = null)
data class SaleDraft(val customerName: String, val customerPhone: String, val lines: List<SaleLine>,
    val payments: List<SalePayment>, val discount: BigDecimal = BigDecimal.ZERO,
    val channel: String = SalesChannelClassification.STORE)
data class SaleTotals(val gross: BigDecimal, val discount: BigDecimal, val net: BigDecimal,
    val paid: BigDecimal, val due: BigDecimal, val change: BigDecimal)
data class SaleRecord(val id: String, val number: Long, val date: Long, val customer: String,
    val phone: String, val total: String, val paid: String, val status: String,
    val channel: String? = null)
enum class SalesPage { ARCHIVE, FULL, QUICK, PAYMENT, DETAILS, RETURNS, PRINT }
