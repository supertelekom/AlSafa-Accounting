package com.alsafa.native8.sales

import java.math.BigDecimal
import java.math.MathContext

/** Store return: restore COGS and reverse discounted invoice revenue. */
object StoreProfitAdjustmentMath {
 private val mc=MathContext.DECIMAL128
 fun returnedProfitDelta(discountedRefundUsd:BigDecimal,unitCostUsd:BigDecimal,quantity:BigDecimal):BigDecimal {
  require(discountedRefundUsd>=BigDecimal.ZERO && unitCostUsd>=BigDecimal.ZERO && quantity>BigDecimal.ZERO)
  return unitCostUsd.multiply(quantity,mc)-discountedRefundUsd
 }
 fun cancellationDelta(originalProfitUsd:BigDecimal,previousDeltas:List<BigDecimal>):BigDecimal =
  -(originalProfitUsd+previousDeltas.fold(BigDecimal.ZERO,BigDecimal::add))
}
