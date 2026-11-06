package com.alsafa.native8.sales

import com.alsafa.native8.data.operational.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

/** DAO fixture tests; not a Room database or device performance benchmark. */
class SalesProductCatalogTest {
 private class Fixture {
  @Volatile var revision="1"
  @Volatile var stock="3"
  val reads=AtomicInteger()
  val dao=Proxy.newProxyInstance(OperationalInventoryWriteDao::class.java.classLoader,arrayOf(OperationalInventoryWriteDao::class.java)){_,method,args ->
   when(method.name) {
    "latestSource"->null
    "searchRevision"->revision
    "searchLocalPage"->{reads.incrementAndGet();val i=args!![0] as Int
     if(i<35)listOf(LocalProductEntity("$i","""{"name":"CASIO $i","stock":"$stock","salePrice":10,"cost":4,"currency":"USD"}""",1,1)) else emptyList<LocalProductEntity>()}
    "searchVariantPage"->emptyList<ProductVariantEntity>()
    else->error("Unexpected DAO read: ${method.name}")
   }
  } as OperationalInventoryWriteDao
 }
 @Test fun repeatedSearchUsesMetadataAndCapsResults()=runBlocking {
  val f=Fixture();val c=SalesProductCatalog(f.dao)
  try {
   c.prepare().await()
   val reads=f.reads.get()
   assertEquals(30,c.find("casio").size)
   assertEquals("12",c.find("CASIO 12").single().productId)
   assertEquals(reads,f.reads.get())
  } finally {c.close()}
 }
 @Test fun revisionRefreshesChangedStock()=runBlocking {
  val f=Fixture();val c=SalesProductCatalog(f.dao)
  try {
   assertEquals("3",c.find("CASIO 12").single().available)
   f.stock="1";f.revision="2"
   assertEquals("1",c.find("CASIO 12").single().available)
  } finally {c.close()}
 }
}
