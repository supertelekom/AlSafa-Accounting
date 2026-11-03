package com.alsafa.native8.sales

import androidx.room.withTransaction
import com.alsafa.native8.data.operational.RoomInventoryRepository
import java.math.BigDecimal

/** Experimental credit purchase posting: stock + posted supplier payable in one Room transaction.
 * No cash movement is created; payment must be zero.
 */
class PurchasePostingService(private val db:AppDatabase) {
 data class Result(val total:BigDecimal,val supplierId:String,val lineCount:Int)
 suspend fun postCredit(draftId:String):Result = db.withTransaction {
  val dao=db.supplierPurchaseDao()
  val draft=dao.draftById(draftId) ?: error("المسودة غير موجودة")
  val lines=dao.draftLines(draftId)
  val totals=PurchasePostingValidator.validate(draft,lines,"0",null)
  require(dao.allSuppliers().any { it.supplier_id==draft.supplier_id }) { "المورد غير موجود" }
  val inventory=RoomInventoryRepository(db)
  for(line in lines) {
   require(inventory.resolved(line.product_source,line.product_id)!=null) { "منتج غير موجود: "+line.product_name }
  }
  check(dao.markDraftPosted(draftId)==1) { "الفاتورة معتمدة مسبقاً" }
  for(line in lines) {
   inventory.adjust(line.product_source,line.product_id,null,line.quantity_exact.toBigDecimal(),"شراء معتمد: "+draftId)
  }
  Result(totals.total,draft.supplier_id,lines.size)
 }
}
