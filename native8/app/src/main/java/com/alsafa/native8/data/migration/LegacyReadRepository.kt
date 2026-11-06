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
class LegacyReadRepository(private val database: LegacyStagingDatabase): com.alsafa.native8.sales.HistoricalFinancialSource {
    data class LegacyRow(val id: String?, val ordinal: Int, val fields: JSONObject)
    data class Page(val section: String, val rows: List<LegacyRow>, val total: Int)

    override fun latestRunId(): Long? =
        database.readableDatabase.rawQuery(
            "SELECT id FROM import_runs ORDER BY id DESC LIMIT 1", null
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else null }

    override fun financialRows(run:Long,section:String):List<JSONObject> {
        val result=mutableListOf<JSONObject>()
        var offset=0
        while(true) {
            val current=page(run,section,offset,500)
            result.addAll(current.rows.map { it.fields })
            offset+=current.rows.size
            if(offset>=current.total) return invoiceMetadata(run,section,result)
            check(current.rows.isNotEmpty()) { "Incomplete legacy section $section" }
        }
    }

    private var metadataRun:Long?=null
    private var metadataSales=emptyMap<String,com.alsafa.native8.sales.InvoiceMetadataProjection>()
    private var metadataRepairs=emptyMap<String,com.alsafa.native8.sales.InvoiceMetadataProjection>()
    /** Load once in the caller's coroutine/Room transaction; never block a Room transaction with nested runBlocking. */
    suspend fun withInvoiceMetadata(db:com.alsafa.native8.sales.AppDatabase):LegacyReadRepository {
        val run=latestRunId() ?: return this
        val source=database.readableDatabase.rawQuery("SELECT source_sha256 FROM import_runs WHERE id=?",arrayOf(run.toString())).use{c -> if(c.moveToFirst())c.getString(0) else return this}
        metadataSales=db.invoiceMetadataDao().editedSales(source).associateBy{it.id}
        metadataRepairs=db.invoiceMetadataDao().editedRepairs(source).associateBy{it.id}
        metadataRun=run
        return this
    }
    /** Overlay only display/date metadata; original source rows and amounts remain intact. */
    private fun invoiceMetadata(run:Long,section:String,rows:List<JSONObject>):List<JSONObject> {
        if(run!=metadataRun || section !in listOf("sales","repairs","returns"))return rows
        val edits=metadataSales to metadataRepairs
        for(raw in rows) {
            if(section=="returns") {
                val group=if(raw.optString("referenceType")=="sale")edits.first else if(raw.optString("referenceType")=="repair")edits.second else continue
                group[raw.optString("referenceId")]?.let{raw.put("invoiceNo",it.number)}
            } else {
                val group=if(section=="sales")edits.first else edits.second
                group[raw.optString("id")]?.let { edit ->
                    raw.put("invoiceNo",edit.number).put("date",edit.date).put("time",edit.time).put("customer",edit.customer).put("phone",edit.phone)
                }
            }
        }
        return rows
    }

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

    /** Resolve sibling colors by the source variantGroupId; retain each variant's own ID. */
    fun productVariants(runId: Long, variantGroupId: String): List<LegacyRow> {
        if (variantGroupId.isBlank()) return emptyList()
        val db = database.readableDatabase
        requireRun(db, runId)
        val result = mutableListOf<LegacyRow>()
        // JSON1 may not be available on all supported Android versions. Stream rows
        // from SQLite rather than assuming json_extract is installed.
        db.rawQuery(
            "SELECT legacy_id, ordinal, json FROM legacy_records WHERE run_id=? AND section='products' ORDER BY ordinal",
            arrayOf(runId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val raw = cursor.getString(2)
                val obj = JSONObject(raw)
                if (obj.optString("variantGroupId") == variantGroupId) {
                    result += LegacyRow(cursor.getString(0), cursor.getInt(1), obj)
                }
            }
        }
        return result
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
