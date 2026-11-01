package com.alsafa.native8.data.migration

import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

/** Read-only integrity checks. Never repairs historical records automatically. */
class LegacyImportInspector(private val database: LegacyStagingDatabase) {
    data class SectionCount(val section: String, val count: Int)
    data class Issue(val section: String, val recordId: String?, val detail: String)
    data class Inspection(val sections: List<SectionCount>, val issues: List<Issue>)

    fun inspect(runId: Long): Inspection {
        val db = database.readableDatabase
        db.rawQuery("SELECT id FROM import_runs WHERE id=?", arrayOf(runId.toString())).use { cursor ->
            require(cursor.moveToFirst()) { "Unknown import run: $runId" }
        }
        val counts = mutableListOf<SectionCount>()
        db.rawQuery("SELECT section, COUNT(*) FROM legacy_records WHERE run_id=? GROUP BY section ORDER BY section",
            arrayOf(runId.toString())).use { c ->
            while (c.moveToNext()) counts += SectionCount(c.getString(0), c.getInt(1))
        }
        val issues = mutableListOf<Issue>()
        val products = ids(db, runId, "products")
        val parts = ids(db, runId, "maintenanceParts")
        val suppliers = ids(db, runId, "suppliers")
        for (section in listOf("products", "maintenanceParts", "sales", "repairs", "suppliers", "purchases", "payments", "returns", "cashTransactions", "inventoryMovements", "partnerWithdrawals", "supplierLedger")) {
            db.rawQuery("SELECT legacy_id,COUNT(*) FROM legacy_records WHERE run_id=? AND section=? AND legacy_id IS NOT NULL GROUP BY legacy_id HAVING COUNT(*)>1",
                arrayOf(runId.toString(), section)).use { c ->
                while (c.moveToNext()) issues += Issue(section, c.getString(0), "Duplicate ID, count=" + c.getInt(1))
            }
        }
        inspectRows(db, runId, "purchases") { row, id ->
            val supplier = row.optString("supplierId")
            if (supplier.isNotEmpty() && supplier !in suppliers)
                issues += Issue("purchases", id, "Unresolved supplierId=" + supplier)
        }
        inspectRows(db, runId, "supplierLedger") { row, id ->
            val supplier = row.optString("supplierId")
            if (supplier.isNotEmpty() && supplier !in suppliers)
                issues += Issue("supplierLedger", id, "Unresolved supplierId=" + supplier)
        }
        inspectRows(db, runId, "sales") { row, id ->
            val items = row.optJSONArray("items")
            if (items != null) for (i in 0 until items.length()) {
                val product = items.optJSONObject(i)?.optString("productId").orEmpty()
                if (product.isNotEmpty() && product !in products)
                    issues += Issue("sales", id, "Unresolved items[" + i + "].productId=" + product)
            }
        }
        inspectRows(db, runId, "repairs") { row, id ->
            val items = row.optJSONArray("usedParts")
            if (items != null) for (i in 0 until items.length()) {
                val part = items.optJSONObject(i)?.optString("partId").orEmpty()
                if (part.isNotEmpty() && part !in parts)
                    issues += Issue("repairs", id, "Unresolved usedParts[" + i + "].partId=" + part)
            }
        }
        inspectRows(db, runId, "inventoryMovements") { row, id ->
            val item = row.optString("itemId")
            val kind = row.optString("itemType")
            val known = if (kind == "part" || kind == "maintenancePart") parts else products
            if (item.isNotEmpty() && item !in known)
                issues += Issue("inventoryMovements", id, "Historical unresolved itemId=" + item + ", itemType=" + kind)
        }
        return Inspection(counts, issues)
    }

    private fun inspectRows(db: SQLiteDatabase, runId: Long, section: String, fn: (JSONObject, String?) -> Unit) {
        db.rawQuery("SELECT legacy_id,json FROM legacy_records WHERE run_id=? AND section=?",
            arrayOf(runId.toString(), section)).use { c ->
            while (c.moveToNext()) fn(JSONObject(c.getString(1)), c.getString(0))
        }
    }

    private fun ids(db: SQLiteDatabase, runId: Long, section: String): Set<String> {
        val result = mutableSetOf<String>()
        db.rawQuery("SELECT legacy_id FROM legacy_records WHERE run_id=? AND section=? AND legacy_id IS NOT NULL",
            arrayOf(runId.toString(), section)).use { c ->
            while (c.moveToNext()) result += c.getString(0)
        }
        return result
    }
}
