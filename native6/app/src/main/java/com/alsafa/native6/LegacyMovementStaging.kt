package com.alsafa.native6

import android.content.ContentValues
import org.json.JSONObject

/**
 * Immutable movement preparation for a future atomic financial posting.
 * No balance, cash, inventory, receipt or partner entry is created here.
 */
object LegacyMovementStaging {
    private val sections = listOf("payments","cashTransactions","supplierLedger",
        "partnerWithdrawals","dailyClosings","returns")
    private const val table = "legacy_movement_staging"

    fun stage(store:Store):JSONObject = store.transaction {
        val meta = LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val hash = meta.getString("sourceSha256")
        require(hash.isNotBlank()) { "بصمة النسخة القديمة مفقودة" }
        val expected = meta.getJSONObject("sourceCollections")
        val db = store.db
        db.execSQL("""CREATE TABLE IF NOT EXISTS legacy_movement_staging(
            source_hash TEXT NOT NULL,
            category TEXT NOT NULL,
            source_id TEXT NOT NULL,
            payload TEXT NOT NULL,
            PRIMARY KEY(source_hash,category,source_id)
        )""")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS legacy_movement_staging_no_update BEFORE UPDATE ON legacy_movement_staging BEGIN SELECT RAISE(ABORT,'Immutable historical movement'); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS legacy_movement_staging_no_delete BEFORE DELETE ON legacy_movement_staging BEGIN SELECT RAISE(ABORT,'Immutable historical movement'); END")
        val existing = store.scalar("SELECT count(*) FROM $table")
        if (existing > 0L) {
            require(store.scalar("SELECT count(*) FROM $table WHERE source_hash=?",hash)==existing) {
                "توجد حركات من نسخة أخرى"
            }
        }
        var verified = 0L
        val bySection = JSONObject()
        for (section in sections) {
            val archive = store.legacyRows(section)
            require(archive.size==expected.optInt(section,0)) {
                "عدد الحركات غير مطابق: $section"
            }
            for (row in archive) {
                val sourceId = row.getValue("legacy_id")
                val payload = row.getValue("payload")
                require(sourceId.isNotBlank() && payload.isNotBlank()) {
                    "حركة قديمة بلا معرف أو بيانات"
                }
                JSONObject(payload)
                // Avoid loading a potentially huge JSON payload through CursorWindow.
                val prior = store.scalar("SELECT count(*) FROM $table WHERE source_hash=? AND category=? AND source_id=?",
                    hash,section,sourceId)
                require(prior in 0L..1L) { "حركة مكررة: $section/$sourceId" }
                if (prior == 1L) {
                    val bytes = store.scalar("SELECT length(CAST(payload AS BLOB)) FROM $table WHERE source_hash=? AND category=? AND source_id=?",
                        hash,section,sourceId)
                    val originalBytes = payload.toByteArray(Charsets.UTF_8)
                    require(bytes == originalBytes.size.toLong()) {
                        "حجم الحركة المجهزة يختلف عن المصدر: $section/$sourceId"
                    }
                    // Verify every byte of an already-staged movement, in bounded
                    // chunks. Equal length alone does not establish identity.
                    var offset = 0
                    while (offset < originalBytes.size) {
                        val count = minOf(4096, originalBytes.size - offset)
                        val chunk = store.rows(
                            "SELECT hex(substr(CAST(payload AS BLOB),?,?)) AS chunk FROM $table WHERE source_hash=? AND category=? AND source_id=?",
                            (offset + 1).toString(), count.toString(), hash, section, sourceId
                        ).single().getValue("chunk")
                        val expectedHex = buildString(count * 2) {
                            val digits = "0123456789ABCDEF"
                            for (i in offset until offset + count) {
                                val byte = originalBytes[i].toInt() and 255
                                append(digits[byte ushr 4])
                                append(digits[byte and 15])
                            }
                        }
                        require(chunk == expectedHex) {
                            "محتوى الحركة المجهزة يختلف عن المصدر: $section/$sourceId"
                        }
                        offset += count
                    }
                } else {
                    val values = ContentValues().apply {
                        put("source_hash",hash)
                        put("category",section)
                        put("source_id",sourceId)
                        put("payload",payload)
                    }
                    db.insertOrThrow(table,null,values)
                }
                verified++
            }
            bySection.put(section,archive.size)
        }
        require(store.scalar("SELECT count(*) FROM $table")==verified) {
            "عدد الحركات المجهزة غير مطابق"
        }
        JSONObject().put("verified",verified).put("sourceSha256",hash)
            .put("sections",bySection).put("posted",false)
    }
}
