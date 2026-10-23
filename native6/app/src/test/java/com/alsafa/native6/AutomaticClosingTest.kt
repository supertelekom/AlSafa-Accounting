package com.alsafa.native6

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class AutomaticClosingTest {
    private lateinit var helper:StoreDb
    private lateinit var store:Store

    @Before fun setUp() {
        helper=StoreDb(RuntimeEnvironment.getApplication() as Context,
            "closing-test-"+System.nanoTime()+".db")
        store=Store(helper)
    }

    @After fun tearDown() { helper.close() }

    @Test fun automaticCloseIsBookOnlyAndIdempotent() {
        val yesterday=LocalDate.now().minusDays(1).toString()
        store.cash("opening","drawer",Currency.USD,10000L,"Opening",date=yesterday)
        assertTrue(store.autoCloseDay("drawer",yesterday))
        assertFalse(store.autoCloseDay("drawer",yesterday))
        val lines=store.closingForDay("drawer",yesterday)
        assertEquals(3,lines.size)
        val usd=lines.single { it.getValue("currency")=="USD" }
        assertEquals("AUTO_CLOSING",usd.getValue("kind"))
        assertEquals(10000L,usd.getValue("expected").toLong())
        assertEquals("",usd["counted"])
        assertEquals(10000L,store.balance("drawer",Currency.USD))
        assertEquals(0L,store.balance("drawer",Currency.TRY))
    }

    @Test fun missedDayClosingPreservesDistinctCurrencies() {
        val old=LocalDate.now().minusDays(3).toString()
        store.cash("opening","drawer",Currency.USD,10000L,"USD opening",date=old)
        store.cash("opening","drawer",Currency.TRY,20000L,"TRY opening",date=old)
        assertEquals(3,store.autoCloseMissingDays("drawer",7))
        assertEquals(0,store.autoCloseMissingDays("drawer",7))
        val yesterday=LocalDate.now().minusDays(1).toString()
        val lines=store.closingForDay("drawer",yesterday)
        assertEquals(10000L,lines.single { it.getValue("currency")=="USD" }.getValue("expected").toLong())
        assertEquals(20000L,lines.single { it.getValue("currency")=="TRY" }.getValue("expected").toLong())
        assertEquals(0L,lines.single { it.getValue("currency")=="SYP" }.getValue("expected").toLong())
        assertTrue(lines.all { it["counted"].isNullOrEmpty() })
    }

    @Test fun reviewOfAutomaticCloseRecordsDifferenceWithoutChangingCash() {
        val yesterday=LocalDate.now().minusDays(1).toString()
        store.cash("opening","drawer",Currency.USD,10000L,"Opening",date=yesterday)
        assertTrue(store.autoCloseDay("drawer",yesterday))
        val journalBefore=store.scalar("SELECT count(*) FROM journal")
        val difference=store.reviewAutomaticClosing("drawer",yesterday,Currency.USD,9500L,"Counted on next day")
        assertEquals(-500L,difference)
        assertEquals(journalBefore,store.scalar("SELECT count(*) FROM journal"))
        assertEquals(10000L,store.balance("drawer",Currency.USD))
        assertEquals(1,store.automaticReviewExceptions("drawer",7).size)
        try {
            store.reviewAutomaticClosing("drawer",yesterday,Currency.USD,9500L,"Duplicate review")
            fail("Second review must be rejected")
        } catch (_:IllegalArgumentException) { }
    }
    @Test fun automaticCloseNeverClosesTodayOrInventsHistory() {
        assertFalse(store.autoCloseDay("drawer",LocalDate.now().minusDays(1).toString()))
        assertEquals(0,store.autoCloseMissingDays("drawer",31))
        try {
            store.autoCloseDay("drawer",LocalDate.now().toString())
            fail("Automatic close is restricted to completed days")
        } catch (_:IllegalArgumentException) { }
        assertEquals(0L,store.scalar("SELECT count(*) FROM closings"))
    }

    @Test fun backdatedCashMovementFlagsClosingDriftWithoutChangingSnapshot() {
        val day=LocalDate.now().minusDays(2).toString()
        store.cash("opening","drawer",Currency.USD,10000L,"Opening",date=day)
        assertTrue(store.autoCloseDay("drawer",day))
        val original=store.closingForDay("drawer",day)
            .single { it.getValue("currency")=="USD" }.getValue("expected").toLong()
        store.cash("opening","external",Currency.USD,5000L,"Other vault",date=day)
        store.cash("transfer","external",Currency.USD,2000L,"Late recorded transfer",
            date=day,destination="drawer")
        assertEquals(10000L,original)
        assertEquals(10000L,store.closingForDay("drawer",day)
            .single { it.getValue("currency")=="USD" }.getValue("expected").toLong())
        assertEquals(2000L,store.closingBookDrift("drawer",day,Currency.USD))
        assertEquals(12000L,store.vaultDayClosing("drawer",Currency.USD,day))
    }

    @Test fun consolidatedClosingDriftAuditFindsOnlyAffectedVaultAndCurrency() {
        val day=LocalDate.now().minusDays(2).toString()
        val month=java.time.YearMonth.parse(day.substring(0,7)).toString()
        store.cash("opening","drawer",Currency.USD,10000L,"Opening",date=day)
        store.cash("opening","external",Currency.USD,5000L,"External opening",date=day)
        assertTrue(store.autoCloseDay("drawer",day))
        assertTrue(store.autoCloseDay("external",day))
        assertTrue(store.allClosingDriftIssues(month).isEmpty())
        val journalBefore=store.scalar("SELECT count(*) FROM journal")
        store.cash("transfer","external",Currency.USD,2000L,"Late transfer",
            date=day,destination="drawer")
        val afterTransfer=store.scalar("SELECT count(*) FROM journal")
        assertTrue(afterTransfer>journalBefore)
        val issues=store.allClosingDriftIssues(month)
        assertEquals(2,issues.size)
        assertEquals(2000L,issues.single { it.getValue("vault")=="drawer" }
            .getValue("book_drift").toLong())
        assertEquals(-2000L,issues.single { it.getValue("vault")=="external" }
            .getValue("book_drift").toLong())
        assertTrue(issues.all { it.getValue("currency")=="USD" })
        assertEquals(afterTransfer,store.scalar("SELECT count(*) FROM journal"))
    }

    @Test fun lateBackdatedCashMovementFlagsOriginalClosingDriftWithoutRewritingSnapshot() {
        val old=LocalDate.now().minusDays(3).toString()
        store.cash("opening","drawer",Currency.USD,10000L,"Opening",date=old)
        assertTrue(store.autoCloseDay("drawer",old))
        val snapshot=store.closingForDay("drawer",old)
            .single { it.getValue("currency")=="USD" }.getValue("expected").toLong()
        store.cash("expense","drawer",Currency.USD,1500L,"Late-entered expense",date=old)
        assertEquals(snapshot,store.closingForDay("drawer",old)
            .single { it.getValue("currency")=="USD" }.getValue("expected").toLong())
        assertEquals(-1500L,store.closingBookDrift("drawer",old,Currency.USD))
        assertEquals(8500L,store.vaultDayClosing("drawer",Currency.USD,old))
        assertEquals(8500L,store.balance("drawer",Currency.USD))
    }

    @Test fun autoCloseDoesNotDuplicateManualClosing() {
        val old=LocalDate.now().minusDays(2).toString()
        store.cash("opening","drawer",Currency.USD,10000L,"Opening",date=old)
        assertTrue(store.autoCloseDay("drawer",old))
        assertEquals(1L,store.scalar(
            "SELECT count(DISTINCT c.event_id) FROM closings c JOIN events e ON e.id=c.event_id WHERE c.vault=? AND e.date=?",
            "drawer",old))
        assertEquals(0,store.autoCloseMissingDays("drawer",1))
    }

}
