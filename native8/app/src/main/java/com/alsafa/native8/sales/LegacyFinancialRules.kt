package com.alsafa.native8.sales

import java.math.BigDecimal
import java.math.MathContext

/**
 * Pure calculation rules transcribed from assets/main.js v3.9.359.
 * Inputs must be actual dated legacy/Room events; this object never fabricates
 * payments, exchange rates, or historical profit.
 */
object LegacyFinancialRules {
 private val math=MathContext.DECIMAL128
 private val zero=BigDecimal.ZERO
 data class Money(val amount:BigDecimal,val usdPerUnit:BigDecimal) {
  init { require(usdPerUnit>zero) { "Historical exchange snapshot is required" } }
  fun usd():BigDecimal=amount.multiply(usdPerUnit,math)
 }
 data class Sale(val date:String,val online:Boolean,val total:Money,val tax:Money,val cost:Money)
 data class Receipt(val date:String,val total:Money,val tax:Money,val cost:Money,
  val invoiceTotal:BigDecimal,val amount:BigDecimal, val discount:BigDecimal=BigDecimal.ZERO) {
  init { require(invoiceTotal>zero && amount>=zero && discount>=zero && amount+discount<=invoiceTotal) }
  fun profit():BigDecimal {
   val revenueRatio=amount.divide(invoiceTotal,math)
   val costRatio=(amount+discount).divide(invoiceTotal,math)
   return (total.usd()-tax.usd()).multiply(revenueRatio,math)-cost.usd().multiply(costRatio,math)
  }
 }

 /** Original main.js grossProfitUsdForRange return branches.
  * All monetary inputs are invoice-currency values; fx is USD per unit at return time.
  */
 data class SaleReturn(
  val date:String,val netCredit:BigDecimal,val returnedCost:BigDecimal,
  val replacementCost:BigDecimal,val recognizedRatio:BigDecimal?,
  val usdPerUnit:BigDecimal
 ) {
  fun impactUsd():BigDecimal {
   require(usdPerUnit>zero)
   val ratio=(recognizedRatio ?: BigDecimal.ONE).coerceIn(zero,BigDecimal.ONE)
   return (returnedCost-replacementCost-netCredit).multiply(ratio,math).multiply(usdPerUnit,math)
  }
 }
 data class RepairReturn(
  val date:String,val refundAmount:BigDecimal,val extraCollected:BigDecimal,
  val returnedCost:BigDecimal,val replacementCost:BigDecimal,
  val taxRatePercent:BigDecimal,val beforeRecognition:BigDecimal?,
  val afterRecognition:BigDecimal?,val usdPerUnit:BigDecimal
 ) {
  fun impactUsd():BigDecimal {
   require(usdPerUnit>zero && taxRatePercent>=zero)
   val divisor=BigDecimal.ONE+taxRatePercent.divide(BigDecimal(100),math)
   val before=(beforeRecognition ?: BigDecimal.ONE).coerceIn(zero,BigDecimal.ONE)
   val after=(afterRecognition ?: before).coerceIn(zero,BigDecimal.ONE)
   val value=extraCollected.divide(divisor,math)-refundAmount.divide(divisor,math)+
    returnedCost.multiply(before,math)-replacementCost.multiply(after,math)
   return value.multiply(usdPerUnit,math)
  }
 }
 fun returnAdjustments(sales:List<SaleReturn>,repairs:List<RepairReturn>):List<Adjustment> =
  sales.map { Adjustment(it.date,it.impactUsd()) }+
   repairs.map { Adjustment(it.date,it.impactUsd()) }

 data class Adjustment(val date:String,val profitImpactUsd:BigDecimal)
 data class Expense(val date:String,val money:Money)
 data class Withdrawal(val date:String,val partnerId:String,val money:Money)
 data class Source(
  val sales:List<Sale>,val onlineReceipts:List<Receipt>,
  val repairReceipts:List<Receipt>,val returns:List<Adjustment>,
  val expenses:List<Expense>,val withdrawals:List<Withdrawal>
 )
 data class Breakdown(val store:BigDecimal,val online:BigDecimal,val repair:BigDecimal,
  val returns:BigDecimal,val expenses:BigDecimal) {
  val net:BigDecimal get()=store+online+repair+returns-expenses
 }
 data class Partner(val id:String,val share:Int)
 val partners=listOf(Partner("father",40),Partner("abdulkarim",35),Partner("yazan",25))
 init { check(partners.sumOf {it.share}==100) }
 fun inRange(date:String,from:String,to:String):Boolean =
  (from.isBlank() || date>=from) && (to.isBlank() || date<=to)
 fun breakdown(source:Source,from:String,to:String):Breakdown {
  require(from.isBlank() || to.isBlank() || from<=to)
  fun date(d:String)=inRange(d,from,to)
  val store=source.sales.filter { !it.online && date(it.date) }
   .fold(zero) {sum,s->sum+s.total.usd()-s.tax.usd()-s.cost.usd()}
  val online=source.onlineReceipts.filter {date(it.date)}
   .fold(zero) {sum,r->sum+r.profit()}
  val repair=source.repairReceipts.filter {date(it.date)}
   .fold(zero) {sum,r->sum+r.profit()}
  val returns=source.returns.filter {date(it.date)}
   .fold(zero) {sum,r->sum+r.profitImpactUsd}
  val expenses=source.expenses.filter {date(it.date)}
   .fold(zero) {sum,r->sum+r.money.usd()}
  return Breakdown(store,online,repair,returns,expenses)
 }
 data class PartnerStatement(val periodShare:BigDecimal,val periodWithdrawals:BigDecimal,
  val periodDifference:BigDecimal,val cumulativeDue:BigDecimal)
 fun statement(source:Source,partner:Partner,from:String,to:String,asOf:String):PartnerStatement {
  val factor=BigDecimal(partner.share).divide(BigDecimal(100),math)
  val period=breakdown(source,from,to).net.multiply(factor,math)
  val withdrawn=source.withdrawals.filter {it.partnerId==partner.id && inRange(it.date,from,to)}
   .fold(zero) {sum,row->sum+row.money.usd()}
  val all=breakdown(source,"",asOf).net.multiply(factor,math)
  val totalWithdrawn=source.withdrawals.filter {it.partnerId==partner.id && it.date<=asOf}
   .fold(zero) {sum,row->sum+row.money.usd()}
  return PartnerStatement(period,withdrawn,period-withdrawn,all-totalWithdrawn)
 }
}
