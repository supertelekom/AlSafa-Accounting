package com.alsafa.native6.accounting

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Explicit, transactional configuration. Never rewrites historical partner allocations. */
class PartnerRegistry(private val db: SQLiteDatabase) {
    private fun <T> write(block: () -> T): T {
        db.beginTransaction()
        try {
            val value = block()
            db.setTransactionSuccessful()
            return value
        } finally {
            db.endTransaction()
        }
    }

    fun addPartner(name: String, id: String = UUID.randomUUID().toString()): String = write {
        require(name.isNotBlank() && id.isNotBlank())
        val values = ContentValues().apply { put("id", id); put("name", name.trim()) }
        db.insertOrThrow("partner_members", null, values)
        id
    }

    fun setShares(
        effectiveDate: LocalDate,
        percentages: Map<String, BigDecimal>,
        note: String = "",
        id: String = UUID.randomUUID().toString()
    ): String = write {
        require(percentages.isNotEmpty() && id.isNotBlank())
        require(percentages.values.all { it >= BigDecimal.ZERO && it <= BigDecimal("100") })
        require(percentages.values.fold(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal("100")) == 0)
        val existing = db.rawQuery(
            "SELECT count(*) FROM partner_share_periods WHERE effective_date=?",
            arrayOf(effectiveDate.toString())
        ).use { c -> c.moveToFirst(); c.getLong(0) }
        require(existing == 0L) { "A share schedule already exists on this date" }
        val values = ContentValues().apply {
            put("id", id); put("effective_date", effectiveDate.toString()); put("note", note)
        }
        db.insertOrThrow("partner_share_periods", null, values)
        percentages.forEach { (partner, percent) ->
            val line = ContentValues().apply {
                put("period_id", id); put("partner_id", partner); put("percent", percent.toPlainString())
            }
            db.insertOrThrow("partner_share_lines", null, line)
        }
        id
    }

    /** Register only a posted journal drawing, never create cash movements here. */
    fun linkPostedDrawing(
        eventId: String,
        partnerId: String,
        actualDate: LocalDate,
        currency: String,
        amountMinor: Long,
        id: String = UUID.randomUUID().toString()
    ): String = write {
        require(currency in setOf("USD", "TRY", "SYP") && amountMinor > 0)
        val eventDate = db.rawQuery("SELECT date FROM events WHERE id=?", arrayOf(eventId))
            .use { c -> require(c.moveToFirst()) { "Event does not exist" }; c.getString(0) }
        require(eventDate == actualDate.toString()) { "Drawing date must match its posted event" }
        val posted = db.rawQuery(
            "SELECT coalesce(sum(amount),0) FROM journal WHERE event_id=? AND account=? AND currency=?",
            arrayOf(eventId, "equity:withdrawal:$partnerId", currency)
        ).use { c -> c.moveToFirst(); c.getLong(0) }
        require(posted == amountMinor) { "Journal drawing does not match amount or partner" }
        val values = ContentValues().apply {
            put("id", id); put("partner_id", partnerId); put("event_id", eventId)
            put("actual_date", actualDate.toString()); put("currency", currency); put("amount_minor", amountMinor)
        }
        db.insertOrThrow("partner_drawings", null, values)
        id
    }
}
