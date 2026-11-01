package com.alsafa.native8.data.operational

import com.alsafa.native8.data.migration.LegacyStagingDatabase
import java.security.MessageDigest

/** Read-only comparison of original IDs and full JSON, including embedded images. */
class ProductSnapshotAudit(
    private val staging: LegacyStagingDatabase,
    private val operational: OperationalDatabase
) {
    data class Result(val staged: Int, val operational: Int, val mismatches: Int, val examples: List<String>) {
        val matches: Boolean get() = staged == operational && mismatches == 0
    }

    fun inspect(runId: Long, source: String): Result {
        val db = operational.readableDatabase
        val stagedDb = staging.readableDatabase
        val total = stagedDb.rawQuery(
            "SELECT COUNT(*) FROM legacy_records WHERE run_id=? AND section='products'",
            arrayOf(runId.toString())
        ).use { c -> c.moveToFirst(); c.getInt(0) }
        val count = db.rawQuery(
            "SELECT COUNT(*) FROM products WHERE source_sha256=?",
            arrayOf(source)
        ).use { c -> c.moveToFirst(); c.getInt(0) }
        var mismatches = 0
        val examples = mutableListOf<String>()
        val digest = MessageDigest.getInstance("SHA-256")
        fun hash(value: String): ByteArray = digest.digest(value.toByteArray(Charsets.UTF_8))
        stagedDb.rawQuery(
            "SELECT ordinal, legacy_id, json FROM legacy_records WHERE run_id=? AND section='products' ORDER BY ordinal",
            arrayOf(runId.toString())
        ).use { staged ->
            db.rawQuery(
                "SELECT source_ordinal, legacy_id, original_json FROM products WHERE source_sha256=? ORDER BY source_ordinal",
                arrayOf(source)
            ).use { actual ->
                var hasActual = actual.moveToNext()
                while (staged.moveToNext()) {
                    val ordinal = staged.getInt(0)
                    while (hasActual && actual.getInt(0) < ordinal) {
                        mismatches++
                        if (examples.size < 5) examples.add("سجل تشغيلي زائد: " + actual.getInt(0))
                        hasActual = actual.moveToNext()
                    }
                    if (!hasActual || actual.getInt(0) != ordinal) {
                        mismatches++
                        if (examples.size < 5) examples.add("سجل ناقص: " + ordinal)
                    } else {
                        val sameId = staged.getString(1) == actual.getString(1)
                        val sameJson = hash(staged.getString(2)).contentEquals(hash(actual.getString(2)))
                        if (!sameId || !sameJson) {
                            mismatches++
                            if (examples.size < 5) examples.add("محتوى مختلف عند: " + ordinal)
                        }
                        hasActual = actual.moveToNext()
                    }
                }
                while (hasActual) {
                    mismatches++
                    if (examples.size < 5) examples.add("سجل تشغيلي زائد: " + actual.getInt(0))
                    hasActual = actual.moveToNext()
                }
            }
        }
        return Result(total, count, mismatches, examples)
    }
}
