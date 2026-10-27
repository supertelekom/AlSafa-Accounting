package com.alsafa.native6

import org.junit.Assert.*
import org.junit.Test

class SaleInvoiceFilterTest {
    private val rows = listOf(
        SaleInvoiceFilter.Row("a", "2026-10-26", "12:00", "100", "أحمد", "099123", listOf("Citizen")),
        SaleInvoiceFilter.Row("b", "2026-10-27", "10:00", "101", "خالد", "098456", listOf("Casio"))
    )

    @Test fun emptyQueryShowsSelectedDayOnly() {
        assertEquals(listOf("b"), SaleInvoiceFilter.filter(rows, "", "2026-10-27").map { it.id })
    }
    @Test fun searchIncludesOlderDatesAndAllIndexedFields() {
        for (term in listOf("100", "أحمد", "099123", "citizen")) {
            assertEquals(listOf("a"), SaleInvoiceFilter.filter(rows, term, "2026-10-27").map { it.id })
        }
    }
    @Test fun explicitRangeRestrictsTextSearch() {
        assertTrue(SaleInvoiceFilter.filter(rows, "Citizen", "2026-10-27", "2026-10-27", "2026-10-27").isEmpty())
    }
    @Test fun invalidRangeRejected() {
        try {
            SaleInvoiceFilter.filter(rows, "", "2026-10-27", "2026-10-28", "2026-10-27")
            fail("Expected invalid range")
        } catch (_: IllegalArgumentException) { }
    }
}
