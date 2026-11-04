package com.alsafa.native8.sales

import com.alsafa.native8.maintenance.MaintenanceDao
import com.alsafa.native8.maintenance.MaintenanceRepairEntity
import java.time.LocalDate

/**
 * Delivery audit only. A delivered repair is NOT automatically a recognized
 * profit event: actual collections, cost and historical FX must be reconciled.
 * Never posts imported repairs to operational cash or profit ledgers.
 */
class MaintenanceProfitAuditRepository(private val maintenance:MaintenanceDao) {
 data class Audit(
  val delivered:List<MaintenanceRepairEntity>,
  val unresolvedIds:List<String>,
  val complete:Boolean=false
 ) {
  fun requireComplete():List<MaintenanceRepairEntity> {
   check(complete && unresolvedIds.isEmpty()) {
    "Maintenance profit requires verified receipts, cost and historical exchange"
   }
   return delivered
  }
 }
 suspend fun inspect(from:String,to:String):Audit {
  val first=LocalDate.parse(from)
  val last=LocalDate.parse(to)
  require(!last.isBefore(first))
  val rows=maintenance.deliveredInPeriod(from,to)
  // This repository intentionally does not treat received as profit or assume
  // that invoice price minus part stock cost is the original receipt rule.
  return Audit(rows,rows.map { it.id })
 }
}
