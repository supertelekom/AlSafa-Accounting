package com.alsafa.native8.sales

import androidx.room.withTransaction
import java.math.BigDecimal

/**
 * New operational entries only. Imported historical entries remain read-only.
 * Stable caller IDs make retries idempotent; cash and source record commit together.
 */
class OperationalExpensePartnerRepository(private val db:AppDatabase) {
 private val dao get()=db.operationalExpensePartnerDao()
 private fun amount(value:String):BigDecimal =
  value.toBigDecimal().also { require(it>BigDecimal.ZERO) { "المبلغ يجب أن يكون موجباً" } }
 private fun rate(value:String):BigDecimal =
  value.toBigDecimal().also { require(it>BigDecimal.ZERO) { "سعر الصرف التاريخي مطلوب" } }
 private fun currency(value:String):String =
  value.uppercase().also { require(it in setOf("USD","TRY","SYP")) { "عملة غير مدعومة" } }
 private fun vault(value:String,unit:String):String {
  val normalized=UnifiedCashboxBalance.canonical(value) ?: error("خزنة غير معروفة")
  return normalized
 }
 private fun movementId(id:String)= "financial-"+id
 private fun validateId(id:String) {
  require(id.isNotBlank() && id.length<=150 && id.all { it.isLetterOrDigit() || it=='-' || it=='_' }) {
   "معرف العملية غير صالح"
  }
 }
 private fun validateDate(date:String) {
  require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) { "التاريخ يجب أن يكون YYYY-MM-DD" }
  java.time.LocalDate.parse(date)
 }
 suspend fun saveExpense(row:OperationalExpenseEntity):OperationalExpenseEntity=db.withTransaction {
  validateId(row.id);validateDate(row.date)
  val existing=dao.expense(row.id)
  if(existing!=null) {
   require(existing==row) { "معرف المصروف مستخدم لعملية مختلفة" }
   return@withTransaction existing
  }
  require(dao.withdrawal(row.id)==null) { "معرف المصروف مستخدم لسحب شريك" }
  val unit=currency(row.currency)
  val value=amount(row.amount)
  rate(row.usd_per_unit)
  require(row.category.isNotBlank() && row.payment_method.isNotBlank())
  require(row.created_at>0)
  val box=row.vault?.let { vault(it,unit) }
  val expected=ExpensePaymentVault.route(row.payment_method)
  require(expected==box) { "طريقة الدفع لا تطابق الخزنة" }
  if(box==null) {
   require(row.cash_movement_id==null) { "مصروف دون خزنة لا ينشئ حركة نقدية" }
   dao.insertExpense(row)
  } else {
   require(row.cash_movement_id==movementId(row.id)) { "مرجع حركة الخزنة غير مطابق" }
   val balance=UnifiedCashboxBalance(AppDatabase.cashContext(),db.supplierPurchaseDao()).calculate(box,unit)
   require(balance.verified && balance.total>=value) { "رصيد الخزنة غير معتمد أو غير كافٍ" }
   dao.insertExpense(row)
   db.supplierPurchaseDao().insertCashMovement(RoomCashboxMovement(
    row.cash_movement_id ?: error("مرجع حركة مفقود"),box,unit,value.negate().toPlainString(),
    row.id,row.created_at,"مصروف: "+row.category+" — "+row.notes))
  }
  row
 }
 suspend fun saveWithdrawal(row:OperationalPartnerWithdrawalEntity):OperationalPartnerWithdrawalEntity=db.withTransaction {
  validateId(row.id);validateDate(row.date)
  val existing=dao.withdrawal(row.id)
  if(existing!=null) {
   require(existing==row) { "معرف السحب مستخدم لعملية مختلفة" }
   return@withTransaction existing
  }
  require(dao.expense(row.id)==null) { "معرف السحب مستخدم لمصروف" }
  require(row.partner_id in setOf("father","me","brother")) { "شريك غير معتمد" }
  val unit=currency(row.currency)
  val box=vault(row.vault,unit)
  val value=amount(row.amount);rate(row.usd_per_unit)
  require(row.cash_movement_id==movementId(row.id)) { "مرجع حركة الخزنة غير مطابق" }
  require(row.created_at>0)
  val balance=UnifiedCashboxBalance(AppDatabase.cashContext(),db.supplierPurchaseDao()).calculate(box,unit)
  require(balance.verified && balance.total>=value) { "رصيد الخزنة غير معتمد أو غير كافٍ" }
  dao.insertWithdrawal(row)
  db.supplierPurchaseDao().insertCashMovement(RoomCashboxMovement(row.cash_movement_id,box,unit,
   value.negate().toPlainString(),row.id,row.created_at,"سحب شريك: "+row.partner_id+" — "+row.notes))
  row
 }
}
