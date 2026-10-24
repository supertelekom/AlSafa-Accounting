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
class RepairDateSearchTest {
    @Test fun repairsAreScopedToIntakeDateAndCustomer() {
        val s=Store(StoreDb(RuntimeEnvironment.getApplication(),"repair-range-${Store.id()}.db"))
        val today=LocalDate.now()
        val yesterday=today.minusDays(1).toString()
        val current=today.toString()
        s.receiveRepair("علي","0933000000","Casio","زجاج",yesterday)
        s.receiveRepair("أحمد","0944000000","Citizen","بطارية",current)
        val earlier=s.repairListByDates(from=yesterday,until=yesterday)
        assertEquals(1,earlier.size)
        assertEquals("علي",earlier.single().getValue("customer"))
        val later=s.repairListByDates(query="0944",from=current,until=current)
        assertEquals(1,later.size)
        assertEquals("أحمد",later.single().getValue("customer"))
        assertTrue(s.repairListByDates(query="زجاج",from=current,until=current).isEmpty())
    }

    @Test fun rejectsInvertedDateRange() {
        val s=Store(StoreDb(RuntimeEnvironment.getApplication(),"repair-range-${Store.id()}.db"))
        val today=LocalDate.now()
        try {
            s.repairListByDates(from=today.toString(),until=today.minusDays(1).toString())
            fail("Inverted dates must be rejected")
        } catch (_:IllegalArgumentException) { }
    }
}
