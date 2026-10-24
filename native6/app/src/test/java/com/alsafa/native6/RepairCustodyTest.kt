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

    @Test fun custodyPaginationDoesNotRepeatOrSkipWatches() {
        val s=Store(StoreDb(RuntimeEnvironment.getApplication(),"custody-page-${Store.id()}.db"))
        val yesterday=LocalDate.now().minusDays(1).toString()
        repeat(6) { i ->
            s.receiveRepair("عميل "+i,"0933"+i,"Casio "+i,"زجاج",yesterday)
        }
        val first=s.uncollectedRepairs(minimumDays=1,limit=2,offset=0)
        val second=s.uncollectedRepairs(minimumDays=1,limit=2,offset=2)
        val third=s.uncollectedRepairs(minimumDays=1,limit=2,offset=4)
        assertEquals(2,first.size)
        assertEquals(2,second.size)
        assertEquals(2,third.size)
        assertEquals(6,(first+second+third).map { it.getValue("id") }.toSet().size)
        assertTrue(s.uncollectedRepairs(minimumDays=1,limit=2,offset=6).isEmpty())
    }

    @Test fun custodyRejectsFutureAsOfDates() {
        val s=Store(StoreDb(RuntimeEnvironment.getApplication(),"custody-${Store.id()}.db"))
        try {
            s.uncollectedRepairs(LocalDate.now().plusDays(1).toString())
            fail("Future dates must be rejected")
        } catch (_:IllegalArgumentException) { }
    }
}
