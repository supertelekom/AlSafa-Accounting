package com.alsafa.native8.data.migration

import android.content.Context
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Read-only source audit: never post historical records to the operational ledger.
 * Section names are discovered from the imported backup rather than assumed.
 */
class LegacyCashboxHistory(context: Context) {
    private val staging = LegacyStagingDatabase(context.applicationContext)
    data class Section(val name: String, val count: Int)
    data class Entry(val section: String, val ordinal: Int, val sourceId: String?, val raw: String)
    data class CashMovement(
        val id: String, val ordinal: Int, val vault: String, val currency: String,
        val amount: BigDecimal, val type: String, val date: String,
        val referenceId: String, val referenceType: String
    )
    data class CashHistory(val movements: List<CashMovement>, val invalid: Int, val duplicates: Int)
    /** Historical projection only: never replay these entries into Room cashbox_movements. */
    fun cashMovements(): CashHistory {
        val result = mutableListOf<CashMovement>()
        val seen = mutableSetOf<String>()
        var invalid = 0
        var duplicates = 0
        staging.readableDatabase.rawQuery(
            """SELECT ordinal, json FROM legacy_records
               WHERE run_id=(SELECT MAX(id) FROM import_runs) AND section='cashTransactions'
               ORDER BY ordinal""", null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                try {
                    val ordinal = cursor.getInt(0)
                    val obj = JSONObject(cursor.getString(1))
                    val id = obj.optString("id")
                    val vault = obj.optString("vault")
                    val currency = obj.optString("currency")
                    val amount = obj.getString("amount").toBigDecimal()
                    if (id.isBlank() || vault.isBlank() || currency.isBlank()) {
                        invalid++
                        continue
                    }
                    if (!seen.add(id)) {
                        duplicates++
                        continue
                    }
                    result += CashMovement(id, ordinal, vault, currency, amount,
                        obj.optString("type"), obj.optString("date"),
                        obj.optString("referenceId"), obj.optString("referenceType"))
                } catch (_: Exception) { invalid++ }
            }
        }
        return CashHistory(result, invalid, duplicates)
    }
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
