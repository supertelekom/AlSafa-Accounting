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
                val prior = store.rows("SELECT payload FROM $table WHERE source_hash=? AND category=? AND source_id=?",
                    hash,section,sourceId).singleOrNull()
                if (prior != null) {
                    require(prior.getValue("payload")==payload) {
                        "حركة مجهزة تختلف عن مصدرها: $section/$sourceId"
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
