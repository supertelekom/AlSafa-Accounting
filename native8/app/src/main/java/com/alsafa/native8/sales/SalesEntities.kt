package com.alsafa.native8.sales

import java.math.BigDecimal

const val MANUAL_SALE_SOURCE = "manual"

data class SaleLine(val productSource: String, val productId: String, val variantId: String? = null,
    val title: String, val quantity: BigDecimal, val unitPrice: BigDecimal, val unitCost: BigDecimal,
    val discount: BigDecimal = BigDecimal.ZERO)
data class SalePayment(val cashbox: String, val currency: String, val amount: BigDecimal,
    val invoiceCurrency: String? = null, val fxRate: BigDecimal? = null,
    val creditedAmount: BigDecimal? = null)
data class SaleDraft(val customerName: String, val customerPhone: String, val lines: List<SaleLine>,
    val payments: List<SalePayment>, val discount: BigDecimal = BigDecimal.ZERO,
    val channel: String = SalesChannelClassification.STORE,
    val invoiceAt:Long?=null,val warrantyMonths:Int=0,val notes:String="",
    val changeCashbox:String?=null,val changeCurrency:String?=null,val changeRate:BigDecimal?=null,
    val invoiceCurrency:String="USD",val invoiceRate:BigDecimal=BigDecimal.ONE, val senderShipping:SenderShipping?=null)
data class SaleTotals(val gross: BigDecimal, val discount: BigDecimal, val net: BigDecimal,
    val paid: BigDecimal, val due: BigDecimal, val change: BigDecimal)
data class SaleRecord(val id: String, val number: Long, val date: Long, val customer: String,
    val phone: String, val total: String, val paid: String, val status: String,
    val channel: String? = null,val currency:String="USD",val remaining:String="0",val items:String="",val historical:Boolean=false)
enum class SalesPage { ARCHIVE, FULL, QUICK, PAYMENT, DETAILS, RETURNS, PRINT }

data class SenderShipping(val amount:BigDecimal,val currency:String,val cashbox:String)
