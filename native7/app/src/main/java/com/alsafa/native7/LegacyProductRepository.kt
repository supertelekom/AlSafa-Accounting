package com.alsafa.native7

import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

/** Read-only, paginated product listing over imported typed SQLite records.
 * This is intentionally separate from write operations and does not parse
 * the entire 60+ MB original backup on each product-screen visit.
 */
internal object LegacyProductRepository {
    data class Product(val id:String,val name:String,val barcode:String,val stock:Int,
        val currency:String,val salePrice:Double,val cost:Double,
        val variantGroupId:String,val variantRole:String,val raw:JSONObject)
    fun list(db:SQLiteDatabase,search:String="",offset:Int=0,limit:Int=50):List<Product> {
        require(offset>=0 && limit in 1..200)
        NativeProductChanges.create(db)
        val term=search.trim()
        val where=if(term.isBlank())"" else
            "WHERE name LIKE ? ESCAPE '!' OR barcode LIKE ? ESCAPE '!' OR model LIKE ? ESCAPE '!' OR sku LIKE ? ESCAPE '!'"
        val escaped=term.replace("!","!!").replace("%","!%").replace("_","!_")
        val args=if(term.isBlank())arrayOf(limit.toString(),offset.toString())
            else arrayOf(*Array(4){"%$escaped%"},limit.toString(),offset.toString())
        val sql="""SELECT payload FROM (
            SELECT COALESCE(e.payload,p.raw_json) payload,
              COALESCE(json_extract(e.payload,'$.name'),p.name) name,
              COALESCE(json_extract(e.payload,'$.barcode'),p.barcode) barcode,
              p.model model,p.sku sku,p.ordinal sort_order
            FROM domain_products p LEFT JOIN native_product_edits e ON e.product_id=p.id
            UNION ALL
            SELECT COALESCE(e.payload,n.payload) payload,
              json_extract(COALESCE(e.payload,n.payload),'$.name') name,
              json_extract(COALESCE(e.payload,n.payload),'$.barcode') barcode,
              json_extract(COALESCE(e.payload,n.payload),'$.model') model,
              json_extract(COALESCE(e.payload,n.payload),'$.sku') sku,
              1000000+n.created_at sort_order
            FROM native_new_products n LEFT JOIN native_product_edits e ON e.product_id=n.product_id
        ) $where ORDER BY sort_order LIMIT ? OFFSET ?"""
        val output=ArrayList<Product>()
        db.rawQuery(sql,args).use { c ->
            while(c.moveToNext()) {
                val row=JSONObject(c.getString(0))
                output.add(Product(row.optString("id"),row.optString("name"),
                    row.optString("barcode"),LegacyProductRules.stock(row),
                    row.optString("currency","USD"),
                    LegacyAccountingRules.number(row.opt("salePrice")),
                    LegacyAccountingRules.number(row.opt("cost")),
                    row.optString("variantGroupId"),row.optString("variantRole"),row))
            }
        }
        return output
    }
    fun findById(db:SQLiteDatabase,id:String):Product? {
        NativeProductChanges.create(db)
        NativeProductChanges.get(db,id)?.let { row ->
            return Product(row.optString("id"),row.optString("name"),
                row.optString("barcode"),LegacyProductRules.stock(row),
                row.optString("currency","USD"),LegacyAccountingRules.number(row.opt("salePrice")),
                LegacyAccountingRules.number(row.opt("cost")),
                row.optString("variantGroupId"),row.optString("variantRole"),row)
        }
        return null
    }
}
