package com.alsafa.native8.home
import org.junit.Test
import org.junit.Assert.*
import java.math.BigDecimal
class HomeIncomeTotalsTest {
 @Test fun currenciesAndRepairIncomeRemainSeparate() {
  val t=HomeIncomeTotals();t.add("a","sale","TRY",BigDecimal("100"));t.add("b","sale","USD",BigDecimal("2"));t.add("c","repair","TRY",BigDecimal("50"))
  assertEquals(BigDecimal("100"),t.sales["TRY"]);assertEquals(BigDecimal("2"),t.sales["USD"]);assertEquals(BigDecimal("50"),t.repairs["TRY"])
 }
 @Test fun duplicateMovementDoesNotDoubleReceipts() {
  val t=HomeIncomeTotals();repeat(2){t.add("a","sale","USD",BigDecimal.TEN)};assertEquals(BigDecimal.TEN,t.sales["USD"])
 }
 @Test fun expensesTransfersAndNegativeAmountsAreNotIncome() {
  val t=HomeIncomeTotals();t.add("a","expense","USD",BigDecimal.TEN);t.add("b","transfer","USD",BigDecimal.TEN);t.add("c","repair","TRY",BigDecimal("-5"))
  assertTrue(t.sales.isEmpty());assertEquals(BigDecimal.ZERO,t.repairs["TRY"])
 }
}
