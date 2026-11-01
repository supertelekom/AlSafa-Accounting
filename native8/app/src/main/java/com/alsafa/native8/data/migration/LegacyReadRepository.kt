package com.alsafa.native8.data.migration

import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

/**
 * Read-only operational projection of staged legacy data.
 *
 * This exposes original IDs and source JSON to UI adapters without re-posting
 * stock, vault or profit movements. It is NOT a final normalized ledger.
 * Consumers must not invent missing fields or recompute financial balances.
 */
class LegacyReadRepository(private val database: LegacyStagingDatabase) {
    data class LegacyRow(val id: String?, val ordinal: Int, val fields: JSONObject)
    data class Page(val section: String, val rows: List<LegacyRow>, val total: Int)

    fun latestRunId(): Long? =
        database.readableDatabase.rawQuery(
            "SELECT id FROM import_runs ORDER BY id DESC LIMIT 1", null
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else null }

    fun page(runId: Long, section: String, offset: Int = 0, limit: Int = 100): Page {
        require(section.isNotBlank())
        require(offset >= 0)
        require(limit in 1..500)
        val db = database.readableDatabase
        requireRun(db, runId)
        val args = arrayOf(runId.toString(), section)
        val total = db.rawQuery(
            "SELECT COUNT(*) FROM legacy_records WHERE run_id=? AND section=?", args
        ).use { c -> c.moveToFirst(); c.getInt(0) }
        val rows = mutableListOf<LegacyRow>()
        db.rawQuery(
            """SELECT legacy_id, ordinal, json FROM legacy_records
               WHERE run_id=? AND section=? ORDER BY ordinal LIMIT ? OFFSET ?""",
            arrayOf(runId.toString(), section, limit.toString(), offset.toString())
        ).use { c ->
            while (c.moveToNext()) {
                rows += LegacyRow(
                    c.getString(0),
                    c.getInt(1),
                    JSONObject(c.getString(2))
                )
            }
        }
        return Page(section, rows, total)
    }

    /** An original ID may be duplicated; never silently pick a financial record. */
    fun byOriginalId(runId: Long, section: String, legacyId: String): LegacyRow? {
        require(section.isNotBlank() && legacyId.isNotBlank())
        val db = database.readableDatabase
        requireRun(db, runId)
        val rows = mutableListOf<LegacyRow>()
        db.rawQuery(
            """SELECT legacy_id, ordinal, json FROM legacy_records
               WHERE run_id=? AND section=? AND legacy_id=? LIMIT 2""",
            arrayOf(runId.toString(), section, legacyId)
        ).use { c ->
            while (c.moveToNext()) rows += LegacyRow(
                c.getString(0), c.getInt(1), JSONObject(c.getString(2))
            )
        }
        check(rows.size <= 1) { "Duplicate legacy ID in $section: $legacyId" }
        return rows.firstOrNull()
    }

    fun objectSection(runId: Long, section: String): String? {
        require(section.isNotBlank())
        val db = database.readableDatabase
        requireRun(db, runId)
        return db.rawQuery(
            "SELECT json FROM legacy_objects WHERE run_id=? AND section=?",
            arrayOf(runId.toString(), "data.$section")
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    private fun requireRun(db: SQLiteDatabase, runId: Long) {
        db.rawQuery("SELECT id FROM import_runs WHERE id=?", arrayOf(runId.toString())).use { c ->
            require(c.moveToFirst()) { "Unknown import run: $runId" }
        }
    }
}
