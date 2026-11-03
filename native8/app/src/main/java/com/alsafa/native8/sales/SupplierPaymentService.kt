package com.alsafa.native8.sales

import androidx.room.withTransaction
import java.math.BigDecimal
import java.util.UUID

/** Collect a subsequent payment for an already posted purchase invoice. */
class SupplierPaymentService(private val db:AppDatabase) {
 suspend fun payArchivedInvoice(archiveKey:String,amount:BigDecimal,cashbox:String):BigDecimal = db.withTransaction {
  require(amount>BigDecimal.ZERO) { "أدخل مبلغاً موجباً" }
  require(cashbox in setOf("drawer","external","shamcash","bank")) { "خزنة غير معروفة" }
  val dao=db.supplierPurchaseDao()
  val invoice=dao.archivedPurchaseByKey(archiveKey) ?: error("الفاتورة القديمة غير موجودة")
  val total=invoice.total_exact.toBigDecimal()
  val importedPaid=invoice.paid_at_import_exact.toBigDecimal()
  require(total>=BigDecimal.ZERO && importedPaid>=BigDecimal.ZERO && importedPaid<=total) { "قيم الأرشيف غير صالحة" }
  val subsequent=dao.newPayments(archiveKey).fold(BigDecimal.ZERO) { sum,row -> sum+row.amount_exact.toBigDecimal() }
  val remaining=total-importedPaid-subsequent
  require(remaining>BigDecimal.ZERO && amount<=remaining) { "المبلغ أكبر من المتبقي على الفاتورة القديمة" }
  val aliases=when(cashbox) {
   "drawer" -> listOf("drawer","الدرج")
   "external" -> listOf("external","الصندوق الخارجي")
   "shamcash" -> listOf("shamcash","sham","شام كاش")
   else -> listOf("bank","البنك")
  }
  val available=dao.cashboxAmounts(aliases,invoice.currency.uppercase()).fold(BigDecimal.ZERO) { sum,value -> sum+value.toBigDecimal() }
  require(available>=amount) { "رصيد الخزنة المسجل غير كافٍ؛ الأرصدة الافتتاحية لم تُطابق بعد" }
  val movementId="purchase-archive-cash-"+UUID.randomUUID()
  val now=System.currentTimeMillis()
  dao.insertCashMovement(RoomCashboxMovement(movementId,cashbox,invoice.currency,
   amount.negate().toPlainString(),archiveKey,now,"تسديد فاتورة شراء قديمة "+invoice.invoice_number))
  dao.insertPayment(SupplierPaymentEntity("archive-pay-"+UUID.randomUUID(),archiveKey,
   invoice.supplier_id,invoice.currency,amount.toPlainString(),cashbox,now,movementId))
  remaining-amount
 }
 suspend fun pay(draftId:String,amount:BigDecimal,cashbox:String):BigDecimal = db.withTransaction {
  require(amount>BigDecimal.ZERO) { "أدخل مبلغاً موجباً" }
  require(cashbox in setOf("drawer","external","shamcash","bank")) { "خزنة غير معروفة" }
  val dao=db.supplierPurchaseDao()
  val draft=dao.draftById(draftId) ?: error("الفاتورة غير موجودة")
  require(draft.status=="POSTED") { "لا يمكن تسديد مسودة غير معتمدة" }
  val total=dao.draftLines(draftId).fold(BigDecimal.ZERO) { sum,line ->
   sum+line.quantity_exact.toBigDecimal()*line.unit_cost_exact.toBigDecimal()
  }
  val paid=dao.newPayments(draftId).fold(BigDecimal.ZERO) { sum,row ->
   sum+row.amount_exact.toBigDecimal()
  }
  val remaining=total-paid
  require(amount<=remaining) { "المبلغ أكبر من المتبقي على الفاتورة" }
  val aliases=when(cashbox) {
   "drawer" -> listOf("drawer","الدرج")
   "external" -> listOf("external","الصندوق الخارجي")
   "shamcash" -> listOf("shamcash","sham","شام كاش")
   else -> listOf("bank","البنك")
  }
  val available=dao.cashboxAmounts(aliases,draft.currency.uppercase()).fold(BigDecimal.ZERO) { sum,value -> sum+value.toBigDecimal() }
  require(available>=amount) { "رصيد الخزنة المسجل غير كافٍ؛ الأرصدة الافتتاحية لم تُطابق بعد" }
  val movementId="purchase-cash-"+UUID.randomUUID()
  val now=System.currentTimeMillis()
  dao.insertCashMovement(RoomCashboxMovement(movementId,cashbox,draft.currency,
   amount.negate().toPlainString(),draftId,now,"تسديد فاتورة شراء"))
  dao.insertPayment(SupplierPaymentEntity("purchase-pay-"+UUID.randomUUID(),draftId,
   draft.supplier_id,draft.currency,amount.toPlainString(),cashbox,now,movementId))
  remaining-amount
 }
}
