package com.alsafa.native8.sales
import java.math.BigDecimal

/** Period earnings and cumulative due are distinct calculations. */
object PartnerFinancialStatement {
 data class Row(val partnerId:String,val percent:Int,val periodProfit:BigDecimal,
  val cumulativeProfit:BigDecimal,val periodWithdrawals:BigDecimal,val cumulativeWithdrawals:BigDecimal) {
  val due get()=cumulativeProfit-cumulativeWithdrawals
 }
 fun calculate(periodNet:BigDecimal,cumulativeNet:BigDecimal,
  periodWithdrawals:Map<String,BigDecimal>,cumulativeWithdrawals:Map<String,BigDecimal>):List<Row> {
  val ids=LegacyFinancialRules.partners.map { it.id }.toSet()
  require((periodWithdrawals.keys+cumulativeWithdrawals.keys).all { it in ids })
  require((periodWithdrawals.values+cumulativeWithdrawals.values).all { it>=BigDecimal.ZERO })
  return LegacyFinancialRules.partners.map {
   val factor=BigDecimal(it.share).divide(BigDecimal(100))
   Row(it.id,it.share,periodNet*factor,cumulativeNet*factor,
    periodWithdrawals[it.id] ?: BigDecimal.ZERO,cumulativeWithdrawals[it.id] ?: BigDecimal.ZERO)
  }
 }
}
