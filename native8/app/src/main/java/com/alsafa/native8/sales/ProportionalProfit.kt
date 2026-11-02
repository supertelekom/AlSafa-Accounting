package com.alsafa.native8.sales

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure allocation helper: call only after a payment has been successfully posted.
 * A caller must persist the result against the payment ID and collection timestamp.
 * No historical FX rates or past collection dates are inferred.
 */
object ProportionalProfit {
    fun forCollection(
        invoiceNet: BigDecimal,
        remainingNetCost: BigDecimal,
        collectedBase: BigDecimal
    ): BigDecimal {
        require(invoiceNet > BigDecimal.ZERO)
        require(remainingNetCost >= BigDecimal.ZERO)
        require(collectedBase > BigDecimal.ZERO && collectedBase <= invoiceNet)
        return (invoiceNet - remainingNetCost)
            .multiply(collectedBase)
            .divide(invoiceNet, 2, RoundingMode.HALF_UP)
    }
}
