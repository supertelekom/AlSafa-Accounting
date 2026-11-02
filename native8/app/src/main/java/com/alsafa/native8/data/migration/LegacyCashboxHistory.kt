package com.alsafa.native8.data.migration

import android.content.Context
import org.json.JSONObject

/**
 * Read-only source audit: never post historical records to the operational ledger.
 * Section names are discovered from the imported backup rather than assumed.
 */
class LegacyCashboxHistory(context: Context) {
    private val staging = LegacyStagingDatabase(context.applicationContext)
    data class Section(val name: String, val count: Int)
    data class Entry(val section: String, val ordinal: Int, val sourceId: String?, val raw: String)
    fun sections(): List<Section> = staging.readableDatabase.rawQuery(
        """SELECT section, COUNT(*) FROM legacy_records
           WHERE run_id=(SELECT MAX(id) FROM import_runs)
           GROUP BY section ORDER BY section""", null
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(Section(cursor.getString(0), cursor.getInt(1)))
        }
    }
    fun entries(section: String, limit: Int = 150): List<Entry> =
        staging.readableDatabase.rawQuery(
            """SELECT ordinal, legacy_id, json FROM legacy_records
               WHERE run_id=(SELECT MAX(id) FROM import_runs) AND section=?
               ORDER BY ordinal DESC LIMIT ?""", arrayOf(section, limit.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Entry(section, cursor.getInt(0),
                    cursor.getString(1), cursor.getString(2)))
            }
        }
}
