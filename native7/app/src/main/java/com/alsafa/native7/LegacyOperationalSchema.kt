package com.alsafa.native7

import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/** Operational record tables based on sections observed in the original source and actual backup.
 * JSON payload preserves every original field; indexed IDs and references support later domain mapping.
 */
internal object LegacyOperationalSchema {
    val sections = setOf("products","maintenanceParts","sales","repairs","expenses","payments","partnerWithdrawals","cashTransactions","dailyClosings","purchases","suppliers","returns","usedWatchTransactions","customerOrders","auditLog","discountRules","loyaltyLedger","productCostHistory","smartReceipts","productDetailOptions","inventoryMovements","inventorySessions","customerSessions","shiftHandovers","supplierLedger")
    fun create(db: SQLiteDatabase) {
        for (section in sections) {
            db.execSQL("CREATE TABLE IF NOT EXISTS op_${section} (ordinal INTEGER PRIMARY KEY, original_id TEXT, reference_id TEXT, date_value TEXT, invoice_no TEXT, payload TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_op_${section}_id ON op_${section}(original_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_op_${section}_ref ON op_${section}(reference_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_op_${section}_date ON op_${section}(date_value)")
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS op_children (section TEXT NOT NULL, parent_ordinal INTEGER NOT NULL, child_key TEXT NOT NULL, child_ordinal INTEGER NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(section,parent_ordinal,child_key,child_ordinal))")
    }
    fun importSection(db: SQLiteDatabase, section: String, rows: JSONArray) {
        if (section !in sections) return
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: throw IllegalArgumentException("Invalid record in $section")
            db.execSQL("INSERT INTO op_${section}(ordinal,original_id,reference_id,date_value,invoice_no,payload) VALUES (?,?,?,?,?,?)",
                arrayOf(i,row.optString("id"),row.optString("referenceId"),
                    row.optString("date"),row.optString("invoiceNo"),row.toString()))
            val keys = row.keys()
            while(keys.hasNext()) {
                val key=keys.next()
                val children=row.optJSONArray(key) ?: continue
                for(j in 0 until children.length()) {
                    db.execSQL("INSERT INTO op_children(section,parent_ordinal,child_key,child_ordinal,payload) VALUES (?,?,?,?,?)",
                        arrayOf(section,i,key,j,children.get(j).toString()))
                }
            }
        }
        db.rawQuery("SELECT COUNT(*) FROM op_${section}",null).use {
            check(it.moveToFirst() && it.getInt(0)==rows.length())
        }
    }
}
