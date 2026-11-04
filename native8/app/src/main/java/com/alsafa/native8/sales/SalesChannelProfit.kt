package com.alsafa.native8.sales

import java.math.BigDecimal
import java.math.MathContext

/**
 * Financial channel separation for verified invoice/receipt events.
 * A caller MUST supply the channel from the original invoice record.
 * This pure calculator does not infer channels from payment status.
 */
object SalesChannelProfit {
 private val mc=MathContext.DECIMAL128
 private val zero=BigDecimal.ZERO
 enum class Channel { STORE, ONLINE }
 data class Invoice(
  val id:String,
  val date:String,
  val channel:Channel,
  val netRevenueUsd:BigDecimal,
  val costUsd:BigDecimal
 ) {
  init { require(id.isNotBlank() && date.isNotBlank() && netRevenueUsd>=zero && costUsd>=zero) }
 }
 data class Collection(
  val id:String,
  val invoiceId:String,
  val date:String,
  val creditedUsd:BigDecimal,
  val discountAllocatedUsd:BigDecimal=zero
 ) {
  init { require(id.isNotBlank() && invoiceId.isNotBlank() && date.isNotBlank() &&
   creditedUsd>zero && discountAllocatedUsd>=zero) }
 }
 data class Breakdown(val store:BigDecimal,val online:BigDecimal) {
  val total:BigDecimal get()=store+online
 }
 fun calculate(invoices:List<Invoice>,collections:List<Collection>,from:String,to:String):Breakdown {
  require(from<=to)
  require(invoices.map {it.id}.distinct().size==invoices.size) { "Duplicate invoice ID" }
  require(collections.map {it.id}.distinct().size==collections.size) { "Duplicate receipt ID" }
  val byId=invoices.associateBy {it.id}
  require(collections.all {it.invoiceId in byId}) { "Receipt without invoice" }
  val store=invoices.filter {it.channel==Channel.STORE && it.date>=from && it.date<=to}
   .fold(zero) { total,i -> total+i.netRevenueUsd-i.costUsd }
  var online=zero
  for(invoice in invoices.filter {it.channel==Channel.ONLINE}) {
   val receipts=collections.filter {it.invoiceId==invoice.id}
   val credited=receipts.fold(zero) {a,r->a+r.creditedUsd+r.discountAllocatedUsd}
   require(credited<=invoice.netRevenueUsd) { "Collections exceed invoice net" }
   if(invoice.netRevenueUsd==zero) {
    require(receipts.isEmpty()) { "Zero-net invoice has collections" }
    continue
   }
   for(receipt in receipts.filter {it.date>=from && it.date<=to}) {
    val revenue=receipt.creditedUsd
    val allocatedCost=invoice.costUsd.multiply(
     receipt.creditedUsd+receipt.discountAllocatedUsd,mc
    ).divide(invoice.netRevenueUsd,mc)
    online+=revenue-allocatedCost
   }
  }
  return Breakdown(store,online)
 }
}
