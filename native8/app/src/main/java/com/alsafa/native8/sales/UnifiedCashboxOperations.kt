package com.alsafa.native8.sales

import androidx.room.withTransaction
import java.math.BigDecimal
import java.util.UUID

/**
 * Unified Room ledger entry points. Historical cash is projected read-only.
 * Every transfer posts two linked movements in a single database transaction.
 */
class UnifiedCashboxOperations(private val db:AppDatabase) {
 data class TransferResult(val reference:String,val sourceBalance:BigDecimal,val destinationBalance:BigDecimal)

 private fun validateCurrency(vault:String,currency:String) {
  require(currency in setOf("USD","TRY","SYP")) { "عملة غير مدعومة" }
  require(vault!="shamcash" || currency in setOf("USD","SYP")) { "شام كاش لا يدعم هذه العملة في السورس الأصلي" }
 }

 private suspend fun spendable(vault:String,currency:String):BigDecimal {
  val balance=UnifiedCashboxBalance(AppDatabase.cashContext(),db.supplierPurchaseDao())
   .calculate(vault,currency)
  require(balance.verified) { "رصيد الخزنة غير مطابق آلياً: "+balance.warning }
  return balance.total
 }

 suspend fun transfer(from:String,to:String,currency:String,amount:BigDecimal,note:String=""):TransferResult =
  db.withTransaction {
   val source=UnifiedCashboxBalance.canonical(from) ?: error("خزنة المصدر غير معروفة")
   val destination=UnifiedCashboxBalance.canonical(to) ?: error("خزنة الوجهة غير معروفة")
   require(source!=destination) { "لا يمكن التحويل إلى الخزنة نفسها" }
   validateCurrency(source,currency.uppercase())
   validateCurrency(destination,currency.uppercase())
   require(amount>BigDecimal.ZERO) { "المبلغ يجب أن يكون موجباً" }
   val available=spendable(source,currency)
   require(available>=amount) { "رصيد المصدر غير كافٍ" }
   val reference="cash-transfer-"+UUID.randomUUID()
   val now=System.currentTimeMillis()
   val dao=db.supplierPurchaseDao()
   dao.insertCashMovement(RoomCashboxMovement(reference+"-out",source,currency.uppercase(),
    amount.negate().toPlainString(),reference,now,"تحويل إلى "+destination+" "+note))
   dao.insertCashMovement(RoomCashboxMovement(reference+"-in",destination,currency.uppercase(),
    amount.toPlainString(),reference,now,"تحويل من "+source+" "+note))
   TransferResult(reference,available-amount,
    UnifiedCashboxBalance(AppDatabase.cashContext(),dao).calculate(destination,currency).total)
  }

 suspend fun record(vault:String,currency:String,amount:BigDecimal,reference:String,note:String):String =
  db.withTransaction {
   val normalized=UnifiedCashboxBalance.canonical(vault) ?: error("خزنة غير معروفة")
   validateCurrency(normalized,currency.uppercase())
   require(amount!=BigDecimal.ZERO) { "المبلغ لا يمكن أن يكون صفراً" }
   require(reference.isNotBlank()) { "مرجع الحركة مطلوب" }
   if(amount<BigDecimal.ZERO) require(spendable(normalized,currency)>=amount.negate()) {
    "رصيد الخزنة غير كافٍ"
   }
   val id="cash-operation-"+UUID.randomUUID()
   db.supplierPurchaseDao().insertCashMovement(RoomCashboxMovement(id,normalized,
    currency.uppercase(),amount.toPlainString(),reference,System.currentTimeMillis(),note))
   id
  }
 /**
  * Currency exchange records actual outgoing and incoming denominations, not an
  * inferred conversion. Both entries share a reference and commit atomically.
  */
 suspend fun exchange(vault:String,fromCurrency:String,toCurrency:String,
  outgoing:BigDecimal,incoming:BigDecimal,note:String=""):String=db.withTransaction {
  val normalized=UnifiedCashboxBalance.canonical(vault) ?: error("خزنة غير معروفة")
  val from=fromCurrency.uppercase()
  val to=toCurrency.uppercase()
  validateCurrency(normalized,from)
  validateCurrency(normalized,to)
  require(from!=to) { "اختر عملتين مختلفتين" }
  require(outgoing>BigDecimal.ZERO && incoming>BigDecimal.ZERO) { "مبالغ الصرف يجب أن تكون موجبة" }
  require(spendable(normalized,from)>=outgoing) { "رصيد العملة المصروفة غير كافٍ" }
  val reference="cash-exchange-"+UUID.randomUUID()
  val now=System.currentTimeMillis()
  val dao=db.supplierPurchaseDao()
  dao.insertCashMovement(RoomCashboxMovement(reference+"-out",normalized,from,
   outgoing.negate().toPlainString(),reference,now,"صرف عملة إلى "+to+" — "+note))
  dao.insertCashMovement(RoomCashboxMovement(reference+"-in",normalized,to,
   incoming.toPlainString(),reference,now,"صرف عملة من "+from+" — "+note))
  reference
 }

}
