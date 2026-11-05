package com.alsafa.native8.sales

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Convert source-verified inputs into read-only reconciliation events.
 * Caller must supply the original event date and historical exchange snapshot.
 * No fallback to current FX, no mutation of inventory or cashboxes.
 */
object VerifiedFinancialEventAdapters {
 fun store(
  originalSaleId:String,date:String,
  netSaleAmount:BigDecimal,costAmount:BigDecimal,
  saleUsdPerUnit:BigDecimal,costUsdPerUnit:BigDecimal
 ):FinancialSourceReconciliation.Event {
  require(originalSaleId.isNotBlank())
  require(netSaleAmount>=BigDecimal.ZERO && costAmount>=BigDecimal.ZERO)
  require(saleUsdPerUnit>BigDecimal.ZERO && costUsdPerUnit>BigDecimal.ZERO)
  return FinancialSourceReconciliation.Event(
   "legacy:store:$originalSaleId",FinancialSourceReconciliation.Stream.LEGACY_STORE,
   LocalDate.parse(date),netSaleAmount*saleUsdPerUnit,costAmount*costUsdPerUnit)
 }
 fun onlineReceipt(
  originalSaleId:String,originalReceiptId:String,date:String,
  collectedUsd:BigDecimal,recognizedCostUsd:BigDecimal
 ):FinancialSourceReconciliation.Event {
  require(originalSaleId.isNotBlank() && originalReceiptId.isNotBlank())
  return FinancialSourceReconciliation.Event(
   "legacy:online:$originalSaleId:$originalReceiptId",
   FinancialSourceReconciliation.Stream.LEGACY_ONLINE,LocalDate.parse(date),
   collectedUsd,recognizedCostUsd)
 }
 fun repairDelivery(
  repairId:String,date:String,revenueUsd:BigDecimal,partsAndRepairCostUsd:BigDecimal
 ):FinancialSourceReconciliation.Event {
  require(repairId.isNotBlank())
  return FinancialSourceReconciliation.Event("repair:delivery:$repairId",
   FinancialSourceReconciliation.Stream.REPAIR,LocalDate.parse(date),
   revenueUsd,partsAndRepairCostUsd)
 }
 fun expense(
  originalExpenseId:String,date:String,amountUsd:BigDecimal
 ):FinancialSourceReconciliation.Event {
  require(originalExpenseId.isNotBlank())
  return FinancialSourceReconciliation.Event("expense:$originalExpenseId",
   FinancialSourceReconciliation.Stream.EXPENSE,LocalDate.parse(date),
   amountUsd,BigDecimal.ZERO)
 }
 fun adjustment(
  originalEventId:String,date:String,profitDeltaUsd:BigDecimal
 ):FinancialSourceReconciliation.Event {
  require(originalEventId.isNotBlank())
  return FinancialSourceReconciliation.Event("return:$originalEventId",
   FinancialSourceReconciliation.Stream.RETURN,LocalDate.parse(date),
   BigDecimal.ZERO,BigDecimal.ZERO,profitDeltaUsd)
 }
}
