package com.alsafa.native6.accounting

import android.content.Context
import org.robolectric.RuntimeEnvironment
import com.alsafa.native6.Currency
import com.alsafa.native6.Store
import com.alsafa.native6.StoreDb
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class PartnerLedgerIntegrationTest {
    private lateinit var context: Context
    private lateinit var helper: StoreDb
    private lateinit var store: Store
    private lateinit var registry: PartnerRegistry

    @Before fun setup() {
        context=RuntimeEnvironment.getApplication()
        helper=StoreDb(context,"partner-ledger-test.db")
        store=Store(helper)
        registry=PartnerRegistry(store.db)
    }

    @After fun cleanup() {
        helper.close()
        context.deleteDatabase("partner-ledger-test.db")
    }

    @Test fun postedDrawingIsLinkedAtomicallyAndBackfillIsIdempotent() {
        registry.addPartner("Partner A","partner-a")
        registry.setShares(LocalDate.of(2026,10,1),mapOf("partner-a" to BigDecimal("100")))
        store.cash("opening","drawer",Currency.USD,10000,"Opening","2026-10-01")
        store.cash("withdrawal","drawer",Currency.USD,1200,"Partner withdrawal","2026-10-05","partner-a")
        val lines=PartnerReportReader(store.db).withdrawals(YearMonth.of(2026,10),"USD")
        assertEquals(1,lines.size)
        assertEquals(1200L,lines.single().amountMinor)
        assertEquals(0,registry.backfillPostedDrawings())
        assertTrue(registry.unlinkedWithdrawals().isEmpty())
    }

    @Test fun leavingPartnerStillAppearsInMonthlyReport() {
        registry.addPartner("First","first")
        registry.addPartner("Second","second")
        registry.setShares(LocalDate.of(2026,10,1),mapOf("first" to BigDecimal("100")))
        registry.setShares(LocalDate.of(2026,10,16),mapOf("second" to BigDecimal("100")))
        val lines=PartnerReportReader(store.db).monthlyFromPostedJournal(YearMonth.of(2026,10),"USD")
        assertEquals(setOf("first","second"),lines.map { it.partnerId }.toSet())
    }
}
