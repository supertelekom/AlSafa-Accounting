package com.alsafa.native6

import java.time.LocalDate

/**
 * Pure filtering rules for the sales archive. Never writes invoices, stock or journals.
 * A text query searches across dates; an empty query defaults to the selected day.
 */
internal object SaleInvoiceFilter {
    data class Row(
        val id: String,
        val date: String,
        val time: String = "",
        val invoiceNumber: String = "",
        val customer: String = "",
        val phone: String = "",
        val productNames: List<String> = emptyList()
    )

    /** Normalize Arabic/Persian digits and common Arabic letter variants for invoice search. */
    internal fun normalize(value: String): String = buildString {
        for (char in value.trim().lowercase()) append(when (char) {
            in '٠'..'٩' -> ('0'.code + char.code - '٠'.code).toChar()
            in '۰'..'۹' -> ('0'.code + char.code - '۰'.code).toChar()
            'أ', 'إ', 'آ' -> 'ا'
            'ى' -> 'ي'
            else -> char
        })
    }

    fun filter(
        rows: List<Row>,
        query: String,
        selectedDay: String,
        from: String? = null,
        to: String? = null
    ): List<Row> {
        val q = normalize(query)
        val start = from?.takeIf { it.isNotBlank() }
        val end = to?.takeIf { it.isNotBlank() }
        require(runCatching { LocalDate.parse(selectedDay) }.isSuccess) { "Invalid selected day" }
        require(start == null || runCatching { LocalDate.parse(start) }.isSuccess) { "Invalid start date" }
        require(end == null || runCatching { LocalDate.parse(end) }.isSuccess) { "Invalid end date" }
        require(start == null || end == null || start <= end) { "Invalid date range" }
        return rows.asSequence().filter { row ->
            val dateMatches = when {
                start != null || end != null -> (start == null || row.date >= start) && (end == null || row.date <= end)
                q.isEmpty() -> row.date == selectedDay
                else -> true
            }
            dateMatches && (q.isEmpty() || sequenceOf(
                row.invoiceNumber, row.customer, row.phone, *row.productNames.toTypedArray()
            ).any { normalize(it).contains(q) })
        }.sortedWith(compareByDescending<Row> { it.date }
            .thenByDescending { it.time }.thenByDescending { it.id }).toList()
    }
}
