package com.alsafa.native8.sales
import com.alsafa.native8.maintenance.MaintenanceDeliveryEligibility
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class MaintenanceDeliveryEligibilityTest {
 @Test fun labourIntakeCanDeliverButDeliveredOrUnfinishedCannot() {
  val raw=JSONObject("""{"laborPrice":250,"currency":"TRY","usedParts":[]}""")
  assertTrue(MaintenanceDeliveryEligibility.allowed("بانتظار التسليم",raw))
  assertTrue(MaintenanceDeliveryEligibility.allowed("جاهز للاستلام",raw))
  listOf("تم التسليم","قيد الصيانة","بانتظار القطعة","ملغاة").forEach { assertFalse(MaintenanceDeliveryEligibility.allowed(it,raw)) }
 }
 @Test fun readyRepairAllowsZeroLabourAndParts() {
  assertTrue(MaintenanceDeliveryEligibility.allowed("بانتظار التسليم",JSONObject("""{"laborPrice":0,"usedParts":[]}""")))
  assertTrue(MaintenanceDeliveryEligibility.allowed("بانتظار التسليم",JSONObject("""{"laborPrice":10,"usedParts":[{"qty":1}]}""")))
 }
}
