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
        val term=search.trim()
        val where=if(term.isBlank())"" else
            "WHERE name LIKE ? ESCAPE '!' OR barcode LIKE ? ESCAPE '!' OR model LIKE ? ESCAPE '!' OR sku LIKE ? ESCAPE '!'"
        val escaped=term.replace("!","!!").replace("%","!%").replace("_","!_")
        val args=if(term.isBlank())arrayOf(limit.toString(),offset.toString())
            else arrayOf(*Array(4){"%$escaped%"},limit.toString(),offset.toString())
        val output=ArrayList<Product>()
        db.rawQuery("SELECT raw_json FROM domain_products $where ORDER BY ordinal LIMIT ? OFFSET ?",args).use { c ->
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
        db.rawQuery("SELECT raw_json FROM domain_products WHERE id=? LIMIT 1",arrayOf(id)).use { c ->
            if(!c.moveToFirst())return null
            val row=JSONObject(c.getString(0))
            return Product(row.optString("id"),row.optString("name"),
                row.optString("barcode"),LegacyProductRules.stock(row),
                row.optString("currency","USD"),LegacyAccountingRules.number(row.opt("salePrice")),
                LegacyAccountingRules.number(row.opt("cost")),
                row.optString("variantGroupId"),row.optString("variantRole"),row)
        }
    }
}
