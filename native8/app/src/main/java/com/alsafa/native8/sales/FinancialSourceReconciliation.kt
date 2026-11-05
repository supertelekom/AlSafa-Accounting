package com.alsafa.native8.sales

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Pure, read-only reconciliation of explicitly verified source events.
 * Never writes to sales, inventory, cashbox or historical archive tables.
 */
object FinancialSourceReconciliation {
 enum class Stream { LEGACY_STORE, LEGACY_ONLINE, REPAIR, RETURN, EXPENSE }
 data class Event(
  val sourceKey:String,
  val stream:Stream,
  val recognizedOn:LocalDate,
  val revenueUsd:BigDecimal,
  val costUsd:BigDecimal,
  val profitAdjustmentUsd:BigDecimal=BigDecimal.ZERO
 ) {
  init {
   require(sourceKey.isNotBlank())
   require(revenueUsd>=BigDecimal.ZERO && costUsd>=BigDecimal.ZERO)
  }
  val profitUsd:BigDecimal get()=revenueUsd-costUsd+profitAdjustmentUsd
 }
 data class Summary(
  val store:BigDecimal,val online:BigDecimal,val repair:BigDecimal,
  val returns:BigDecimal,val expenses:BigDecimal
 ) {
  val net:BigDecimal get()=store+online+repair+returns-expenses
 }
 fun summarize(events:List<Event>,from:LocalDate,to:LocalDate):Summary {
  require(!to.isBefore(from))
  require(events.map { it.sourceKey }.distinct().size==events.size) {
   "Duplicate financial source event"
  }
  val selected=events.filter { !it.recognizedOn.isBefore(from) && !it.recognizedOn.isAfter(to) }
  fun total(stream:Stream)=selected.filter {it.stream==stream}
   .fold(BigDecimal.ZERO) { sum,e -> sum+e.profitUsd }
  return Summary(total(Stream.LEGACY_STORE),total(Stream.LEGACY_ONLINE),
   total(Stream.REPAIR),total(Stream.RETURN),total(Stream.EXPENSE))
 }
}
