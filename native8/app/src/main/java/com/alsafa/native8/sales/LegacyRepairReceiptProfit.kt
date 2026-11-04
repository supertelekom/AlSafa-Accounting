package com.alsafa.native8.sales

import java.math.BigDecimal
import java.math.MathContext

/**
 * Exact receipt-level arithmetic of main.js repairReceiptEvents,
 * repairReceiptCost and grossProfitUsdForRange. Inputs are sourced events,
 * never inferred from a delivery status.
 */
object LegacyRepairReceiptProfit {
 private val mc=MathContext.DECIMAL128
 private val zero=BigDecimal.ZERO
 private val one=BigDecimal.ONE
 data class Receipt(
  val repairId:String,
  val date:String,
  val amount:BigDecimal,
  val invoiceTotal:BigDecimal,
  val invoiceTax:BigDecimal,
  val invoiceCost:BigDecimal,
  val usdPerCurrencyUnit:BigDecimal
 ) {
  init {
   require(repairId.isNotBlank() && date.isNotBlank())
   require(amount>zero && invoiceTotal>=zero && invoiceTax>=zero &&
    invoiceCost>=zero && usdPerCurrencyUnit>zero)
  }
  fun profitUsd():BigDecimal {
   val ratio=if(invoiceTotal>zero)
    amount.divide(invoiceTotal,mc).min(one) else zero
   val costRatio=amount.divide(invoiceTotal.max(one),mc).min(one)
   val net=(invoiceTotal-invoiceTax).max(zero)
   return (net.multiply(ratio,mc)-invoiceCost.multiply(costRatio,mc))
    .multiply(usdPerCurrencyUnit,mc)
  }
 }
 fun sum(events:List<Receipt>):BigDecimal =
  events.fold(zero) { total,event -> total+event.profitUsd() }
}
