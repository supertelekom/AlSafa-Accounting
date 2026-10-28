package com.alsafa.native6.accounting

import java.math.BigInteger

/**
 * Allocates original invoice cost across actual collection amounts, not dates of sale.
 * Inputs are minor currency units. The final installment receives the rounding remainder.
 * No stock or cash is posted by this pure calculator.
 */
object OnlineCollectionAllocation {
    data class Allocation(val capital: Long, val profit: Long)

    fun allocate(
        invoiceTotal: Long,
        originalCost: Long,
        previouslyCollected: Long,
        previouslyRecognizedCost: Long,
        currentCollection: Long
    ): Allocation {
        require(invoiceTotal > 0 && originalCost >= 0)
        require(previouslyCollected in 0..invoiceTotal)
        require(previouslyRecognizedCost in 0..originalCost)
        require(currentCollection > 0 && currentCollection <= invoiceTotal - previouslyCollected)
        val cumulative = previouslyCollected + currentCollection
        val targetCost = if (cumulative == invoiceTotal) originalCost else {
            BigInteger.valueOf(originalCost)
                .multiply(BigInteger.valueOf(cumulative))
                .divide(BigInteger.valueOf(invoiceTotal)).longValueExact()
        }
        require(targetCost >= previouslyRecognizedCost) {
            "Previously recognized cost exceeds the proportional allocation"
        }
        val capital = targetCost - previouslyRecognizedCost
        return Allocation(capital, currentCollection - capital)
    }
}
