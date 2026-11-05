package com.alsafa.native8.sales

import java.math.BigDecimal
import java.math.MathContext

/** Cumulative due is calculated from ALL recognized net profit to asOf, never only the displayed month. */
object CumulativePartnerProfit {
 private val mc=MathContext.DECIMAL128
 data class Balance(
  val partnerId:String,val sharePercent:Int,
  val earnedToDateUsd:BigDecimal,val withdrawnToDateUsd:BigDecimal
 ) {
  val dueUsd:BigDecimal get()=earnedToDateUsd-withdrawnToDateUsd
 }
 fun calculate(netProfitToDateUsd:BigDecimal,withdrawalsToDateUsd:Map<String,BigDecimal>):List<Balance> {
  val partners=LegacyFinancialRules.partners
  require(partners.sumOf {it.share}==100)
  require(withdrawalsToDateUsd.keys.all { key -> partners.any {it.id==key} })
  require(withdrawalsToDateUsd.values.all {it>=BigDecimal.ZERO})
  return partners.map {
   Balance(it.id,it.share,netProfitToDateUsd.multiply(BigDecimal(it.share),mc)
    .divide(BigDecimal(100),mc),withdrawalsToDateUsd[it.id] ?: BigDecimal.ZERO)
  }
 }
}
