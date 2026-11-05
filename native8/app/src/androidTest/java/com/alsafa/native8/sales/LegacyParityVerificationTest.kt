package com.alsafa.native8.sales

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.alsafa.native8.data.migration.LegacyReadRepository
import com.alsafa.native8.data.migration.LegacyStagingDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * Instrumented parity gate against the original v3.9.359 main.js
 * grossProfitUsdForRange on the 2026-10-26 formatVersion=2 backup.
 * Run only on an isolated test device with THAT backup restored into staging.
 * Does not mutate staging, Room, cashbox, stock, or partner records.
 */
@RunWith(AndroidJUnit4::class)
class LegacyParityVerificationTest {
 private val context:Context get()=ApplicationProvider.getApplicationContext()
 private fun number(value:String)=BigDecimal(value)
 private fun assertUsd(expected:String,actual:BigDecimal,label:String) {
  val difference=number(expected).subtract(actual).abs()
  assertTrue("$label: expected $expected USD, actual $actual USD; difference $difference",
   difference<=number("0.000001"))
 }
 private fun staging():LegacyStagingDatabase=LegacyStagingDatabase(context)
 private fun requireFixture(reader:LegacyReadRepository):Long {
  val run=reader.latestRunId() ?: failMissing()
  val db=staging()
  try {
   val c=db.readableDatabase.rawQuery(
    "SELECT format_version,source_sha256 FROM import_runs WHERE id=?",
    arrayOf(run.toString()))
   c.use {
    assertTrue("Import run is missing",it.moveToFirst())
    assertEquals("Expected formatVersion 2 source",2,it.getInt(0))
   }
  } finally { db.close() }
  return run
 }
 private fun failMissing():Nothing {
  throw AssertionError("Restore the 2026-10-26 backup into isolated Native 8 staging before parity testing")
 }
 private fun count(reader:LegacyReadRepository,run:Long,section:String):Int=
  reader.page(run,section,0,1).total

 @Test fun restoredFixtureHasExpectedSections() {
  val db=staging()
  try {
   val reader=LegacyReadRepository(db)
   val run=requireFixture(reader)
   assertEquals(111,count(reader,run,"sales"))
   assertEquals(308,count(reader,run,"repairs"))
   assertEquals(36,count(reader,run,"payments"))
   assertEquals(4,count(reader,run,"returns"))
   assertEquals(22,count(reader,run,"expenses"))
   assertEquals(72,count(reader,run,"partnerWithdrawals"))
  } finally { db.close() }
 }

 @Test fun septemberAndOctoberMatchUnmodifiedLegacyMainJs() {
  val db=staging()
  try {
   val reader=LegacyReadRepository(db)
   val run=requireFixture(reader)
   assertEquals(111,count(reader,run,"sales"))
   val projector=LegacyHistoricalProfitProjection(reader)
   val months=listOf(
    Triple("2026-09-01","2026-09-30","955.6144866395658"),
    Triple("2026-10-01","2026-10-31","2342.218876548387")
   )
   for((from,to,expected) in months) {
    val result=projector.read(from,to)
    assertTrue("Unresolved historical events for $from: ${result.unresolved.joinToString()}",result.unresolved.isEmpty())
    assertUsd(expected,result.partialNetUsd,"Historical gross profit for $from")
   }
  } finally { db.close() }
 }

 @Test fun partnerWithdrawalsAreNotOperatingExpenses() {
  val db=staging()
  try {
   val reader=LegacyReadRepository(db)
   requireFixture(reader)
   val projector=LegacyHistoricalProfitProjection(reader)
   val september=projector.read("2026-09-01","2026-09-30")
   val october=projector.read("2026-10-01","2026-10-31")
   assertUsd("78.35935108662382",september.withdrawalsUsd["father"] ?: BigDecimal.ZERO,"September father withdrawals")
   assertUsd("75.38347107438017",september.withdrawalsUsd["me"] ?: BigDecimal.ZERO,"September Abdul-Karim withdrawals")
   assertUsd("0",september.withdrawalsUsd["brother"] ?: BigDecimal.ZERO,"September Yazan withdrawals")
   assertUsd("259.64880155944286",october.withdrawalsUsd["father"] ?: BigDecimal.ZERO,"October father withdrawals")
   assertUsd("329.48481103237026",october.withdrawalsUsd["me"] ?: BigDecimal.ZERO,"October Abdul-Karim withdrawals")
   assertUsd("81.3963219310924",october.withdrawalsUsd["brother"] ?: BigDecimal.ZERO,"October Yazan withdrawals")
   val partners=LegacyFinancialRules.partners
   assertEquals(setOf("father","me","brother"),partners.map { it.id }.toSet())
   assertEquals(100,partners.sumOf { it.share })
   val netBeforeWithdrawals=september.partialNetUsd+october.partialNetUsd
   assertEquals(0,netBeforeWithdrawals.compareTo(
    september.storeProfitUsd+september.onlineProfitUsd+september.repairProfitUsd+
     september.returnImpactUsd-september.expensesUsd+
     october.storeProfitUsd+october.onlineProfitUsd+october.repairProfitUsd+
     october.returnImpactUsd-october.expensesUsd))
  } finally { db.close() }
 }

 @Test fun repeatReadDoesNotMutateStagingOrChangeProfit() {
  val db=staging()
  try {
   val reader=LegacyReadRepository(db)
   val run=requireFixture(reader)
   val sections=listOf("sales","repairs","payments","returns","expenses","partnerWithdrawals","cashTransactions")
   val before=sections.associateWith { count(reader,run,it) }
   val projector=LegacyHistoricalProfitProjection(reader)
   val first=projector.read("2026-09-01","2026-10-31")
   val second=projector.read("2026-09-01","2026-10-31")
   assertEquals(0,first.partialNetUsd.compareTo(second.partialNetUsd))
   assertEquals(first.unresolved,second.unresolved)
   assertEquals(before,sections.associateWith { count(reader,run,it) })
  } finally { db.close() }
 }
}
