package com.alsafa.native8.sales

import java.math.BigDecimal
import java.math.MathContext

/** assets/main.js: currentSnapshot stores currency units per USD. */
object LegacyFinancialExchange {
 fun usdPerUnit(currency:String,unitsPerUsd:String?):BigDecimal {
  val unit=currency.trim().uppercase()
  require(unit in setOf("USD","TRY","SYP")) { "Unsupported currency" }
  if(unit=="USD") return BigDecimal.ONE
  val rate=unitsPerUsd?.toBigDecimalOrNull()
   ?: error("Missing historical exchange snapshot for $unit")
  require(rate>BigDecimal.ZERO) { "Invalid historical exchange snapshot" }
  return BigDecimal.ONE.divide(rate,MathContext.DECIMAL128)
 }
 fun usd(amount:String,currency:String,unitsPerUsd:String?):BigDecimal =
  amount.toBigDecimal().multiply(usdPerUnit(currency,unitsPerUsd),MathContext.DECIMAL128)
}
