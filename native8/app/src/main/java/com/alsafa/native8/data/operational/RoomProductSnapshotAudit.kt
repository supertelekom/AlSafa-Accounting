package com.alsafa.native8.data.operational

import com.alsafa.native8.data.migration.LegacyStagingDatabase
import com.alsafa.native8.sales.AppDatabase
import java.security.MessageDigest

/** Compare staged original IDs and raw JSON with Room snapshots. */
class RoomProductSnapshotAudit(
    private val staging: LegacyStagingDatabase,
    private val operational: AppDatabase
) {
    data class Result(val staged: Int, val operational: Int, val mismatches: Int, val examples: List<String>) {
        val matches: Boolean get() = staged == operational && mismatches == 0
    }

    suspend fun inspect(runId: Long, source: String): Result {
        val original = mutableListOf<Triple<Int, String, String>>()
        staging.readableDatabase.rawQuery(
            "SELECT ordinal, legacy_id, json FROM legacy_records WHERE run_id=? AND section='products' ORDER BY ordinal",
            arrayOf(runId.toString())
        ).use { c ->
            while (c.moveToNext()) original.add(Triple(c.getInt(0), c.getString(1), c.getString(2)))
        }
        val actual = operational.operationalInventoryReadDao().importedProducts(source)
        val byOrdinal = actual.associateBy { it.source_ordinal }
        var mismatches = 0
        val examples = mutableListOf<String>()
        val digest = MessageDigest.getInstance("SHA-256")
        fun same(a: String, b: String): Boolean =
            digest.digest(a.toByteArray(Charsets.UTF_8)).contentEquals(digest.digest(b.toByteArray(Charsets.UTF_8)))
        for ((ordinal, id, raw) in original) {
            val found = byOrdinal[ordinal]
            if (found == null || found.legacy_id != id || !same(raw, found.original_json)) {
                mismatches++
                if (examples.size < 5) examples.add("اختلاف عند السجل: $ordinal")
            }
        }
        val ordinals = original.mapTo(HashSet()) { it.first }
        for (row in actual) if (row.source_ordinal !in ordinals) {
            mismatches++
            if (examples.size < 5) examples.add("سجل زائد: ${row.source_ordinal}")
        }
        return Result(original.size, actual.size, mismatches, examples)
    }
}
