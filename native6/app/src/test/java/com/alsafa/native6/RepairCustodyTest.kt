package com.alsafa.native6

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class RepairCustodyTest {
    @Test fun custodyCountsUndeliveredWatchesWithoutFinancialEntries() {
        val s=Store(StoreDb(RuntimeEnvironment.getApplication(),"custody-${Store.id()}.db"))
        val yesterday=LocalDate.now().minusDays(1).toString()
        val id=s.receiveRepair("سامي","0933","Casio","زجاج",yesterday)
        val entriesBefore=s.scalar("SELECT count(*) FROM journal")
        val pending=s.uncollectedRepairs(minimumDays=1)
        assertEquals(1,pending.size)
        assertEquals(id,pending.single().getValue("id"))
        assertEquals("1",pending.single().getValue("age_days"))
        assertEquals(entriesBefore,s.scalar("SELECT count(*) FROM journal"))
        assertTrue(s.uncollectedRepairs(minimumDays=2).isEmpty())
    }

    @Test fun custodyRejectsFutureAsOfDates() {
        val s=Store(StoreDb(RuntimeEnvironment.getApplication(),"custody-${Store.id()}.db"))
        try {
            s.uncollectedRepairs(LocalDate.now().plusDays(1).toString())
            fail("Future dates must be rejected")
        } catch (_:IllegalArgumentException) { }
    }
}
