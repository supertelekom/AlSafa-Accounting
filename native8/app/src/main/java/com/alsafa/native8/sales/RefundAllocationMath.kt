package com.alsafa.native8.sales

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Deterministic allocation in invoice-base minor units.
 * Each allocation is bounded by its payment's credited amount less earlier allocations.
 * No exchange rate is inferred: the caller supplies the refund-time rate explicitly.
 */
object RefundAllocationMath {
    private val zero = BigDecimal.ZERO
    private val cent = BigDecimal("0.01")
    data class Source(val paymentId:String, val creditedBase:BigDecimal, val alreadyAllocatedBase:BigDecimal)
    data class Part(val paymentId:String, val baseAmount:BigDecimal)
    fun allocate(requestBase:BigDecimal, sources:List<Source>):List<Part> {
        require(requestBase > zero && requestBase.remainder(cent).compareTo(zero) == 0)
        require(sources.map { it.paymentId }.distinct().size == sources.size)
        var left=requestBase
        val parts=mutableListOf<Part>()
        for (source in sources) {
            require(source.creditedBase >= zero && source.alreadyAllocatedBase >= zero)
            val available=source.creditedBase-source.alreadyAllocatedBase
            require(available >= zero) { "رد سابق يتجاوز الدفعة" }
            val part=available.min(left)
            if (part > zero) parts+=Part(source.paymentId,part)
            left-=part
        }
        require(left.compareTo(zero)==0) { "المرتجع يتجاوز المقبوض المتاح" }
        return parts
    }
    fun convertExact(baseAmount:BigDecimal, rateToBase:BigDecimal, scale:Int=2):BigDecimal {
        require(baseAmount > zero && rateToBase > zero && scale in 0..6)
        val cash=baseAmount.divide(rateToBase,scale,RoundingMode.HALF_UP)
        require(cash > zero)
        val roundTrip=cash.multiply(rateToBase).setScale(2,RoundingMode.HALF_UP)
        require(roundTrip.compareTo(baseAmount.setScale(2,RoundingMode.UNNECESSARY))==0) {
            "فرق تقريب الصرف يتطلب تسوية صريحة، لا تسامحاً صامتاً"
        }
        return cash
    }
}
