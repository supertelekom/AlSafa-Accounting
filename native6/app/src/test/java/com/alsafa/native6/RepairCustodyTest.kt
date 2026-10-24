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

    @Test fun custodyStatusFilterSeparatesReadyFromReceived() {
        val s=Store(StoreDb(RuntimeEnvironment.getApplication(),"custody-status-${Store.id()}.db"))
        val day=LocalDate.now().toString()
        val ready=s.receiveRepair("عميل جاهز","09331","Casio","بطارية",day)
        s.receiveRepair("عميل جديد","09332","Citizen","زجاج",day)
        s.repairReady(ready)
        val readyOnly=s.uncollectedRepairs(status="ready")
        val receivedOnly=s.uncollectedRepairs(status="received")
        assertEquals(1,readyOnly.size)
        assertEquals(ready,readyOnly.single().getValue("id"))
        assertEquals(1,receivedOnly.size)
        assertEquals(2,s.uncollectedRepairs(status="all").size)
    }

    @Test fun complimentaryRepairDeliveryHasNoReceivable() {
        val s=Store(StoreDb(RuntimeEnvironment.getApplication(),"complimentary-${Store.id()}.db"))
        val repair=s.receiveRepair("عميل ضمان","09330","Casio","ضمان",LocalDate.now().toString())
        s.repairReady(repair)
        val journalBefore=s.scalar("SELECT count(*) FROM journal")
        s.deliverRepair(repair,Currency.USD,0L,0L,Store.vaults.first())
        assertEquals("delivered",s.repairDetail(repair).getValue("status"))
        val invoiceId=s.repairInvoice(repair)!!.getValue("id")
        assertEquals(0L,s.invoice(invoiceId).total)
        assertEquals(0L,s.due(invoiceId))
        assertEquals(journalBefore,s.scalar("SELECT count(*) FROM journal"))
        assertTrue(s.uncollectedRepairs().none { it.getValue("id")==repair })
    }

    @Test fun custodyCountMatchesStatusAndMinimumAge() {
        val s=Store(StoreDb(RuntimeEnvironment.getApplication(),"custody-count-${Store.id()}.db"))
        val today=LocalDate.now()
        val old=s.receiveRepair("قديم","0901","Casio","زجاج",today.minusDays(3).toString())
        s.receiveRepair("جديد","0902","Citizen","بطارية",today.toString())
        s.repairReady(old)
        assertEquals(2L,s.uncollectedRepairCount())
        assertEquals(1L,s.uncollectedRepairCount(minimumDays=2))
        assertEquals(1L,s.uncollectedRepairCount(status="ready"))
        assertEquals(1L,s.uncollectedRepairCount(status="received"))
        assertEquals(s.uncollectedRepairCount(status="ready").toInt(),
            s.uncollectedRepairs(status="ready").size)
    }

    @Test fun custodyRejectsFutureAsOfDates() {
        val s=Store(StoreDb(RuntimeEnvironment.getApplication(),"custody-${Store.id()}.db"))
        try {
            s.uncollectedRepairs(LocalDate.now().plusDays(1).toString())
            fail("Future dates must be rejected")
        } catch (_:IllegalArgumentException) { }
    }
}
