package com.alsafa.native8.sales

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Immutable value contract for future per-payment FX persistence.
 * baseCurrency is the invoice currency. One unit of paymentCurrency is
 * worth baseUnitsPerPaymentUnit units of baseCurrency at collection time.
 * Never infer a historical rate from today's currency settings.
 */
data class PaymentExchangeSnapshot(
    val paymentCurrency: String,
    val baseCurrency: String,
    val baseUnitsPerPaymentUnit: String,
    val paidAmount: String,
    val creditedBaseAmount: String
) {
    fun validate() {
        require(paymentCurrency in setOf("USD", "TRY", "SYP"))
        require(baseCurrency in setOf("USD", "TRY", "SYP"))
        val rate = baseUnitsPerPaymentUnit.toBigDecimal()
        val paid = paidAmount.toBigDecimal()
        val credited = creditedBaseAmount.toBigDecimal()
        require(rate > BigDecimal.ZERO && paid > BigDecimal.ZERO && credited >= BigDecimal.ZERO)
        if (paymentCurrency == baseCurrency) {
            require(rate.compareTo(BigDecimal.ONE) == 0)
            require(credited.compareTo(paid) == 0)
        } else {
            require(credited.compareTo(paid.multiply(rate).setScale(2, RoundingMode.HALF_UP)) == 0) {
                "قيمة الدفعة لا تطابق سعر الصرف المحفوظ"
            }
        }
    }
}
