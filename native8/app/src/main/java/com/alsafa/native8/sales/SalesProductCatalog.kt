package com.alsafa.native8.sales

import android.content.Context
import com.alsafa.native8.data.operational.RoomProductEditRepository
import com.alsafa.native8.data.operational.RoomProductStockRepository

import org.json.JSONObject

data class SaleChoice(val source: String,val productId: String,val variantId: String?,
    val title: String,val barcode: String,val available: String,val price: String,val cost: String,val currency:String="USD")

class SalesProductCatalog(context: Context) {
 private val db=AppDatabase.get(context.applicationContext)

 suspend fun find(query: String): List<SaleChoice> {
  if(query.isBlank()) return emptyList()
  val dao=db.operationalInventoryWriteDao()
  val edits=RoomProductEditRepository(db)
  val stock=RoomProductStockRepository(db)
  val results=mutableListOf<SaleChoice>()
  suspend fun add(source:String,id:String,variant:String?,product:JSONObject,title:String) {
   if(results.size>=30) return
   val barcode=product.optString("barcode")
   if(!listOf(title,barcode,product.optString("model"),product.optString("ref"),product.optString("reference"),product.optString("sku")).any { it.contains(query,true) }) return
   results.add(SaleChoice(source,id,variant,title,barcode,
    stock.currentStock(source,id,variant),product.optString("salePrice","0"),product.optString("cost","0"),product.optString("currency","USD")))
  }
  val source=dao.latestSource()
  if(source!=null) {
   var offset=0
   while(results.size<30) {
    val page=dao.productPage(source,500,offset)
    if(page.isEmpty()) break
    for(row in page) {
     val product=edits.resolved(source,row.legacy_id) ?: continue
     add(source,row.legacy_id,null,product,product.optString("name"))
     if(results.size>=30) break
    }
    offset+=page.size
    if(page.size<500) break
   }
  }
  for(local in dao.localProducts()) {
   val product=JSONObject(local.product_json)
   add("local",local.local_id,null,product,product.optString("name"))
   if(results.size>=30) break
  }
  for(variant in dao.allVariants()) {
   val product=JSONObject(variant.variant_json)
   add(variant.parent_source,variant.parent_id,variant.variant_id,product,product.optString("color"))
   if(results.size>=30) break
  }
  return results
 }
}
