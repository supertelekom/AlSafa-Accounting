package com.alsafa.native8.sales

import java.math.BigDecimal

/** Source-aligned invoice-date STORE gross profit in the invoice's USD accounting base. */
object StoreSaleProfitMath {
 fun calculate(netRevenueUsd:BigDecimal, costUsd:BigDecimal):BigDecimal {
  require(netRevenueUsd>=BigDecimal.ZERO && costUsd>=BigDecimal.ZERO)
  return netRevenueUsd-costUsd
 }
}
