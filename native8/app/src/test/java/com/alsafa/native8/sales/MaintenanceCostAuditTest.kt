package com.alsafa.native8.sales

import com.alsafa.native8.maintenance.MaintenanceRepairEntity
import org.junit.Assert.*
import org.junit.Test

class MaintenanceCostAuditTest {
 private fun repair(raw:String)=MaintenanceRepairEntity("r","native8",432,"2026-11-05","10:00",
  "تم التسليم","client","","watch","","","USD","100","100","2026-11-05",1L,raw)
 @Test fun missingHistoricalPartCostDoesNotApproveProfit() {
  val result=MaintenanceCostAudit.inspect(repair("""{"usedParts":[{"qty":"1","price":"30"}]}"""))
  assertFalse(result.complete)
  assertNull(result.partsCostUsd)
 }
 @Test fun originalCostSnapshotCanBeReadWithoutStockMutation() {
  val result=MaintenanceCostAudit.inspect(repair("""{"usedParts":[{"qty":"2","costUsd":"5"}]}"""))
  assertEquals(0,result.partsCostUsd!!.compareTo(java.math.BigDecimal("10")))
 }
}
