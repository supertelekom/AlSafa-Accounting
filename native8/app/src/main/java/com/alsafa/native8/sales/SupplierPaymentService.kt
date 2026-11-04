package com.alsafa.native8.sales

import androidx.room.withTransaction
import java.math.BigDecimal
import java.util.UUID

/** Collect a subsequent payment for an already posted purchase invoice. */
class SupplierPaymentService(private val db:AppDatabase) {
 suspend fun payOpeningDebt(supplierId:String,currency:String,amount:BigDecimal,cashbox:String):BigDecimal = db.withTransaction {
  require(amount>BigDecimal.ZERO) { "أدخل مبلغاً موجباً" }
  require(cashbox in setOf("drawer","external","shamcash","bank")) { "خزنة غير معروفة" }
  val dao=db.supplierPurchaseDao()
  val ledger=dao.historicalLedger(supplierId).filter { it.currency.equals(currency,true) }
  require(ledger.all { it.kind=="opening-debt" || it.kind=="payment" }) { "حركات تاريخية غير معروفة؛ يجب مطابقتها أولاً" }
  val opening=ledger.filter { it.kind=="opening-debt" }.fold(BigDecimal.ZERO) { sum,row -> sum+row.amount_exact.toBigDecimal() }
  val historicalPaid=ledger.filter { it.kind=="payment" }.fold(BigDecimal.ZERO) { sum,row -> sum+row.amount_exact.toBigDecimal() }
  val reference="opening-debt:"+supplierId+":"+currency.uppercase()
  val newPaid=dao.debtPayments(reference).fold(BigDecimal.ZERO) { sum,row -> sum+row.amount_exact.toBigDecimal() }
  val remaining=opening-historicalPaid-newPaid
  require(opening>BigDecimal.ZERO && remaining>BigDecimal.ZERO && amount<=remaining) { "الدين القديم غير متاح أو المبلغ أكبر من المتبقي" }
  val balance=UnifiedCashboxBalance(AppDatabase.cashContext(),dao).calculate(cashbox,currency)
  require(balance.verified) { "رصيد الصندوق التاريخي غير مطابق: "+balance.warning }
  require(balance.total>=amount) { "الرصيد الموحد غير كافٍ: "+balance.total.toPlainString() }
  val movementId="opening-debt-cash-"+UUID.randomUUID()
  val now=System.currentTimeMillis()
  dao.insertCashMovement(RoomCashboxMovement(movementId,cashbox,currency,amount.negate().toPlainString(),
   reference,now,"تسديد الدين القديم للمورد"))
  dao.insertPayment(SupplierPaymentEntity("opening-debt-pay-"+UUID.randomUUID(),reference,
   supplierId,currency,amount.toPlainString(),cashbox,now,movementId))
  remaining-amount
 }
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
  val balance=UnifiedCashboxBalance(AppDatabase.cashContext(),dao).calculate(cashbox,invoice.currency)
  require(balance.verified) { "رصيد الصندوق التاريخي غير مطابق: "+balance.warning }
  require(balance.total>=amount) { "الرصيد الموحد غير كافٍ: "+balance.total.toPlainString() }
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
  val balance=UnifiedCashboxBalance(AppDatabase.cashContext(),dao).calculate(cashbox,draft.currency)
  require(balance.verified) { "رصيد الصندوق التاريخي غير مطابق: "+balance.warning }
  require(balance.total>=amount) { "الرصيد الموحد غير كافٍ: "+balance.total.toPlainString() }
  val movementId="purchase-cash-"+UUID.randomUUID()
  val now=System.currentTimeMillis()
  dao.insertCashMovement(RoomCashboxMovement(movementId,cashbox,draft.currency,
   amount.negate().toPlainString(),draftId,now,"تسديد فاتورة شراء"))
  dao.insertPayment(SupplierPaymentEntity("purchase-pay-"+UUID.randomUUID(),draftId,
   draft.supplier_id,draft.currency,amount.toPlainString(),cashbox,now,movementId))
  remaining-amount
 }
}
