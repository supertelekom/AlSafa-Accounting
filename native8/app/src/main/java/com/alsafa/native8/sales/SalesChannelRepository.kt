package com.alsafa.native8.sales

import androidx.room.withTransaction

/**
 * Source-backed channel classification. No automatic guessing from payments.
 * A classified invoice cannot be silently reclassified.
 */
class SalesChannelRepository(private val db:AppDatabase) {
 suspend fun classify(invoiceId:String,verifiedChannel:String):RoomSale=db.withTransaction {
  require(invoiceId.isNotBlank())
  SalesChannelClassification.requireVerified(verifiedChannel)
  val original=requireNotNull(db.salesPostingDao().sale(invoiceId)) { "الفاتورة غير موجودة" }
  require(original.status!="ملغاة") { "الفاتورة ملغاة" }
  if(original.channel!=null) {
   require(original.channel==verifiedChannel) { "نوع البيع مثبت بقيمة مختلفة" }
   return@withTransaction original
  }
  check(db.salesPostingDao().classifyUnverified(invoiceId,verifiedChannel)==1) {
   "تعذر تثبيت نوع البيع"
  }
  requireNotNull(db.salesPostingDao().sale(invoiceId))
 }
 suspend fun unverifiedCount():Int=db.salesPostingDao().unverifiedChannelCount()
}
