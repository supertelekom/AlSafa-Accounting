package com.alsafa.native7

import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/**
 * Typed, source-derived projections. JSON staging remains authoritative.
 * References are audited, not enforced: historical records can reference
 * deleted products or imported documents not present in this snapshot.
 */
internal object LegacyDomainTables {
    private val fields = linkedMapOf(
        "products" to "id name brand model sku barcode stock currency salePrice cost variantGroupId externalPartnerId",
        "maintenanceParts" to "id name barcode stock currency cost salePrice",
        "sales" to "id invoiceNo date time customer phone currency paymentStatus paymentMethod subtotal discount total paid stockApplied",
        "repairs" to "id invoiceNo date time status customer phone currency price cost received paymentStatus deliveredDate",
        "purchases" to "id date time supplierId supplierName supplierInvoice currency total paid paymentVault",
        "suppliers" to "id name phone",
        "payments" to "id referenceType referenceId amount currency paymentCurrency paymentAmount method date time",
        "cashTransactions" to "id vault currency amount type referenceType referenceId date time",
        "returns" to "id referenceType referenceId invoiceNo kind currency qty refundAmount date time",
        "supplierLedger" to "id supplierId kind amount currency date time vault",
        "inventoryMovements" to "id itemType itemId before after change reason referenceType referenceId date time",
        "partnerWithdrawals" to "id partnerId partnerName amount currency vault date time",
        "expenses" to "id date time category currency amount paymentMethod vault",
        "dailyClosings" to "id date time deviceId deviceName"
    )
    private val numeric = setOf("stock","salePrice","cost","subtotal","discount","total","paid",
        "price","received","amount","paymentAmount","qty","refundAmount","before","after","change")
    private val nested = mapOf(
        "products" to listOf("fundingLots"),
        "sales" to listOf("items","paymentBreakdown"),
        "repairs" to listOf("usedParts"),
        "purchases" to listOf("items","payments"),
        "suppliers" to listOf("openingDebts","directPayments"),
        "returns" to listOf("returnFundingAllocations","replacementFundingAllocations"),
        "inventoryMovements" to listOf("fundingAdjustments"),
        "supplierLedger" to listOf("corrections")
    )
    fun create(db:SQLiteDatabase) {
        for((section,names) in fields) {
            val definitions=names.split(" ").joinToString(",") {
                "\"$it\" " + if(it in numeric) "REAL" else "TEXT"
            }
            db.execSQL("CREATE TABLE IF NOT EXISTS domain_$section (ordinal INTEGER PRIMARY KEY, $definitions, raw_json TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_domain_${section}_id ON domain_$section(id)")
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS domain_children (section TEXT NOT NULL, parent_ordinal INTEGER NOT NULL, relation TEXT NOT NULL, ordinal INTEGER NOT NULL, original_id TEXT, reference_id TEXT, quantity REAL, amount REAL, raw_json TEXT NOT NULL, PRIMARY KEY(section,parent_ordinal,relation,ordinal))")
        db.execSQL("CREATE TABLE IF NOT EXISTS legacy_reference_audit (source_section TEXT NOT NULL, source_ordinal INTEGER NOT NULL, relation TEXT NOT NULL, target_section TEXT NOT NULL, target_id TEXT NOT NULL, resolved INTEGER NOT NULL, PRIMARY KEY(source_section,source_ordinal,relation,target_section,target_id))")
    }
    fun importSection(db:SQLiteDatabase,section:String,records:JSONArray) {
        val names=fields[section]?.split(" ") ?: return
        val quoted=names.joinToString(",") { "\"$it\"" }
        val placeholders=List(names.size+2){"?"}.joinToString(",")
        for(i in 0 until records.length()) {
            val row=records.getJSONObject(i)
            val args=ArrayList<Any?>()
            args.add(i)
            for(field in names) {
                val value=row.opt(field)
                args.add(when {
                    value==null || value==JSONObject.NULL -> null
                    value is Number -> value
                    value is String -> value
                    value is Boolean -> value.toString()
                    else -> value.toString()
                })
            }
            args.add(row.toString())
            db.execSQL("INSERT INTO domain_$section (ordinal,$quoted,raw_json) VALUES ($placeholders)",args.toTypedArray())
            for(relation in nested[section].orEmpty()) {
                val children=row.optJSONArray(relation) ?: continue
                for(j in 0 until children.length()) {
                    val child=children.get(j)
                    val obj=child as? JSONObject
                    db.execSQL("INSERT INTO domain_children(section,parent_ordinal,relation,ordinal,original_id,reference_id,quantity,amount,raw_json) VALUES (?,?,?,?,?,?,?,?,?)",
                        arrayOf(section,i,relation,j,obj?.optString("id"),obj?.optString("productId")
                            ?:obj?.optString("partId")?:obj?.optString("itemId"),
                            obj?.optDouble("qty")?.takeUnless { it.isNaN() },
                            obj?.optDouble("amount")?.takeUnless { it.isNaN() },child.toString()))
                }
            }
        }
        db.rawQuery("SELECT COUNT(*) FROM domain_$section",null).use {
            check(it.moveToFirst() && it.getInt(0)==records.length())
        }
    }
    fun audit(db:SQLiteDatabase) {
        val relations=listOf(
            arrayOf("purchases","supplierId","suppliers"),
            arrayOf("supplierLedger","supplierId","suppliers")
        )
        for(relation in relations) {
            val (source,field,target)=relation
            db.execSQL("INSERT OR REPLACE INTO legacy_reference_audit(source_section,source_ordinal,relation,target_section,target_id,resolved) SELECT '$source',s.ordinal,'$field','$target',s.$field,CASE WHEN t.id IS NULL THEN 0 ELSE 1 END FROM domain_$source s LEFT JOIN domain_$target t ON s.$field=t.id WHERE s.$field IS NOT NULL AND s.$field!=''")
        }
    }
}
