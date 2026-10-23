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
}
