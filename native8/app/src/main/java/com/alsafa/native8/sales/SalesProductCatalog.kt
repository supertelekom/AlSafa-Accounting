package com.alsafa.native8.sales

import android.content.Context
import com.alsafa.native8.data.operational.OperationalDatabase
import com.alsafa.native8.data.operational.OperationalProductRepository
import com.alsafa.native8.data.operational.ProductEditRepository
import com.alsafa.native8.data.operational.ProductStockRepository
import com.alsafa.native8.data.operational.ProductVariantRepository

data class SaleChoice(val source: String,val productId: String,val variantId: String?,
    val title: String,val barcode: String,val available: String,val price: String,val cost: String)

class SalesProductCatalog(private val context: Context) {
    fun find(query: String): List<SaleChoice> {
        if(query.isBlank()) return emptyList()
        val db=OperationalDatabase(context)
        try {
            val stock=ProductStockRepository(db)
            val edits=ProductEditRepository(db)
            val source=OperationalProductRepository(db).latestSource()
            val results=mutableListOf<SaleChoice>()
            fun add(parentSource: String,id: String,variant: String?,title: String,barcode: String,price: String,cost: String) {
                if(results.size>=30) return
                if(!title.contains(query,true) && !barcode.contains(query,true)) return
                val available=stock.currentStock(parentSource,id,variant)
                results.add(SaleChoice(parentSource,id,variant,title,barcode,available,price,cost))
            }
            if(source!=null) {
                db.readableDatabase.rawQuery("SELECT legacy_id FROM products WHERE source_sha256=? ORDER BY source_ordinal",arrayOf(source)).use { cursor ->
                    while(cursor.moveToNext() && results.size<30) {
                        val id=cursor.getString(0)
                        val product=edits.resolved(source,id) ?: continue
                        add(source,id,null,product.optString("name"),product.optString("barcode"),product.optString("salePrice","0"),product.optString("cost","0"))
                    }
                }
            }
            db.readableDatabase.rawQuery("SELECT local_id FROM local_products",null).use { cursor ->
                while(cursor.moveToNext() && results.size<30) {
                    val id=cursor.getString(0)
                    val product=edits.localById(id) ?: continue
                    add("local",id,null,product.optString("name"),product.optString("barcode"),product.optString("salePrice","0"),product.optString("cost","0"))
                }
            }
            db.readableDatabase.rawQuery("SELECT variant_id,parent_source,parent_id,variant_json FROM product_variants_local",null).use { cursor ->
                while(cursor.moveToNext() && results.size<30) {
                    val id=cursor.getString(0)
                    val variant=org.json.JSONObject(cursor.getString(3))
                    add(cursor.getString(1),cursor.getString(2),id,variant.optString("color"),variant.optString("barcode"),variant.optString("salePrice","0"),variant.optString("cost","0"))
                }
            }
            return results
        } finally { db.close() }
    }
}
