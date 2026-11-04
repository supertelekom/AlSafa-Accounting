package com.alsafa.native8.sales
import org.junit.Test
import org.junit.Assert.*
import java.time.ZoneId

class FinancialPeriodGuardTest {
 @Test fun adjacentPeriodsMeet() {
  val zone=ZoneId.of("Asia/Damascus")
  val first=FinancialPeriodGuard.window("2026-11-01","2026-11-05",zone)
  val second=FinancialPeriodGuard.window("2026-11-06","2026-11-30",zone)
  assertEquals(first.endExclusive,second.startInclusive)
 }
 @Test fun localStartDiffersFromUtc() {
  val date="2026-11-05"
  val local=FinancialPeriodGuard.window(date,date,ZoneId.of("Asia/Damascus"))
  val utc=FinancialPeriodGuard.window(date,date,ZoneId.of("UTC"))
  assertNotEquals(local.startInclusive,utc.startInclusive)
 }
}
