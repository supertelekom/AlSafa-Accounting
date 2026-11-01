package com.alsafa.native8.data.operational

import org.json.JSONObject

class OperationalProductRepository(private val database: OperationalDatabase) {
    data class Row(val id: String, val ordinal: Int, val fields: JSONObject)
    data class Page(val rows: List<Row>, val total: Int, val sourceSha256: String)

    fun latestSource(): String? = database.readableDatabase.rawQuery(
        "SELECT source_sha256 FROM source_imports ORDER BY source_run_id DESC LIMIT 1", null
    ).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun sourceForRun(runId: Long): String? = database.readableDatabase.rawQuery(
        "SELECT source_sha256 FROM source_imports WHERE source_run_id=? LIMIT 1",
        arrayOf(runId.toString())
    ).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun byOriginalId(source: String, id: String): Row? {
        val rows = mutableListOf<Row>()
        database.readableDatabase.rawQuery(
            "SELECT legacy_id, source_ordinal, original_json FROM products WHERE source_sha256=? AND legacy_id=? LIMIT 2",
            arrayOf(source, id)
        ).use { c ->
            while (c.moveToNext()) rows.add(Row(c.getString(0), c.getInt(1), JSONObject(c.getString(2))))
        }
        check(rows.size <= 1) { "Duplicate product ID: $id" }
        return rows.firstOrNull()
    }

    fun variants(source: String, groupId: String): List<Row> {
        if (groupId.isBlank()) return emptyList()
        val rows = mutableListOf<Row>()
        database.readableDatabase.rawQuery(
            "SELECT legacy_id, source_ordinal, original_json FROM products WHERE source_sha256=? AND variant_group_id=? ORDER BY source_ordinal",
            arrayOf(source, groupId)
        ).use { c ->
            while (c.moveToNext()) rows.add(Row(c.getString(0), c.getInt(1), JSONObject(c.getString(2))))
        }
        return rows
    }

    fun page(source: String, offset: Int, limit: Int): Page {
        require(offset >= 0 && limit in 1..500)
        val db = database.readableDatabase
        val total = db.rawQuery("SELECT COUNT(*) FROM products WHERE source_sha256=?", arrayOf(source))
            .use { c -> c.moveToFirst(); c.getInt(0) }
        val rows = mutableListOf<Row>()
        db.rawQuery(
            "SELECT legacy_id, source_ordinal, original_json FROM products WHERE source_sha256=? ORDER BY source_ordinal LIMIT ? OFFSET ?",
            arrayOf(source, limit.toString(), offset.toString())
        ).use { c ->
            while (c.moveToNext()) rows.add(Row(c.getString(0), c.getInt(1), JSONObject(c.getString(2))))
        }
        return Page(rows, total, source)
    }
}
