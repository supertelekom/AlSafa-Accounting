package com.alsafa.native8.sales

import com.alsafa.native8.maintenance.MaintenanceDao
import java.math.BigDecimal

/**
 * Read-only coverage of imported and new delivered repairs.
 * Does not count a repair as recognized profit unless receipt, historical
 * part cost and currency evidence are complete.
 */
class MaintenanceFinancialCoverage(private val dao:MaintenanceDao) {
 data class Result(
  val delivered:Int,
  val imported:Int,
  val newRepairs:Int,
  val verifiedCostAndReceipt:Int,
  val unresolvedIds:List<String>,
  val verifiedGrossProfitUsd:BigDecimal
 )
 suspend fun inspect():Result {
  val rows=dao.allDelivered()
  val unresolved=mutableListOf<String>()
  var verified=0
  var profit=BigDecimal.ZERO
  for(row in rows) {
   val evidence=MaintenanceCostAudit.inspect(row)
   if(evidence.complete) {
    verified++
    profit+=evidence.receiptAmount!!-evidence.partsCostUsd!!
   } else unresolved+=row.id
  }
  return Result(rows.size,rows.count { it.source_sha256!="native8" },
   rows.count { it.source_sha256=="native8" },verified,unresolved,profit)
 }
}
