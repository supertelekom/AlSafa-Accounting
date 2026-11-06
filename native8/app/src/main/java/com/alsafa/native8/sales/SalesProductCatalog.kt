package com.alsafa.native8.sales

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

data class SaleChoice(val source:String,val productId:String,val variantId:String?,
 val title:String,val barcode:String,val available:String,val price:String,val cost:String,val currency:String="USD")

/** One cancellable scan at a time; cache only small metadata, never source JSON or photos. */
class SalesProductCatalog internal constructor(private val dao:com.alsafa.native8.data.operational.OperationalInventoryWriteDao) {
 constructor(context:Context):this(AppDatabase.get(context.applicationContext).operationalInventoryWriteDao())
 private data class Entry(val source:String,val id:String,val variant:String?,val fields:Map<String,String>)
 private val gate=Mutex()
 private var revision:String?=null
 private var index=emptyList<Entry>()
 private val loader=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private var preparation:Deferred<Unit>?=null
 @Synchronized fun prepare():Deferred<Unit> {
  val running=preparation
  if(running!=null && !running.isCompleted)return running
  return loader.async { refreshIndex() }.also { preparation=it }
 }
 fun close(){loader.cancel()}
 private suspend fun refreshIndex() { gate.withLock {
   val source=dao.latestSource()
   val version=source.orEmpty()+":"+dao.searchRevision()
   if(revision!=version) {
    val next=mutableListOf<Entry>()
    if(source!=null) {
     var offset=0
     while(true) {
      currentCoroutineContext().ensureActive()
      // Never hold a 500-product page containing base64 photographs.
      val row=dao.productPage(source,1,offset++).singleOrNull() ?: break
      val fields=SalesSearchFields.read(row.original_json).toMutableMap()
      val patch=dao.edit(source,row.legacy_id)
      if(patch!=null) {
       val hash=MessageDigest.getInstance("SHA-256").digest(row.original_json.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it.toInt() and 255)}
       check(hash==patch.base_sha256){"Source snapshot changed; edit needs review"}
       fields.putAll(SalesSearchFields.read(patch.edited_json))
      }
      next+=Entry(source,row.legacy_id,null,fields)
     }
    }
    var offset=0
    while(true) {
     currentCoroutineContext().ensureActive()
     val row=dao.searchLocalPage(offset++).singleOrNull() ?: break
     next+=Entry("local",row.local_id,null,SalesSearchFields.read(row.product_json))
    }
    offset=0
    while(true) {
     currentCoroutineContext().ensureActive()
     val row=dao.searchVariantPage(offset++).singleOrNull() ?: break
     next+=Entry(row.parent_source,row.parent_id,row.variant_id,SalesSearchFields.read(row.variant_json))
    }
    currentCoroutineContext().ensureActive()
    index=next;revision=version
   }
 } }
 suspend fun find(query:String,exactBarcode:Boolean=false):List<SaleChoice> = withContext(Dispatchers.IO) {
  if(query.isBlank()) return@withContext emptyList()
  // Cancelling a keystroke does not restart the expensive initial metadata load.
  prepare().await()
  gate.withLock {
   val found=mutableListOf<SaleChoice>()
   for(entry in index) {
    currentCoroutineContext().ensureActive()
    val f=entry.fields
    if(!SalesSearchFields.matches(f,query,exactBarcode))continue
    var available=f["stock"].orEmpty().ifBlank{"0"}.toBigDecimal()
    if(entry.source!="local" && entry.variant==null) {
     for(m in dao.stockHistory(entry.source,entry.id,null)) available+=m.quantity_delta.toBigDecimal()
    }
    found+=SaleChoice(entry.source,entry.id,entry.variant,f[if(entry.variant==null)"name" else "color"].orEmpty(),
     f["barcode"].orEmpty(),available.stripTrailingZeros().toPlainString(),f["salePrice"] ?: "0",f["cost"] ?: "0",f["currency"] ?: "USD")
    if(found.size==30)break
   }
   found
  }
 }
}
