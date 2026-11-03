package com.alsafa.native8.sales

import android.content.Context
import com.alsafa.native8.data.migration.LegacyCashboxHistory
import java.math.BigDecimal

/**
 * One read-only cash balance projection for a cashbox/currency.
 * Imported history is never copied into the operational ledger.
 * An opening balance is NOT inferred from transactions.
 */
class UnifiedCashboxBalance(private val context:Context,private val dao:SupplierPurchaseDao) {
 data class Result(val historical:BigDecimal,val operational:BigDecimal,
                   val total:BigDecimal,val verified:Boolean,val warning:String)
 companion object {
  fun canonical(raw:String):String?=when(raw.trim().lowercase()) {
   "drawer","الدرج" -> "drawer"
   "external","الصندوق الخارجي" -> "external"
   "shamcash","sham","شام كاش" -> "shamcash"
   "bank","البنك" -> "bank"
   else -> null
  }
  fun aliases(vault:String):List<String>=when(canonical(vault)) {
   "drawer" -> listOf("drawer","الدرج")
   "external" -> listOf("external","الصندوق الخارجي")
   "shamcash" -> listOf("shamcash","sham","شام كاش")
   "bank" -> listOf("bank","البنك")
   else -> error("خزنة غير معروفة: "+vault)
  }
 }
 suspend fun calculate(vault:String,currency:String):Result {
  val normalized=canonical(vault) ?: error("خزنة غير معروفة")
  val unit=currency.uppercase()
  val history=LegacyCashboxHistory(context).cashMovements()
  val old=history.movements.filter { canonical(it.vault)==normalized && it.currency.equals(unit,true) }
  val historical=old.fold(BigDecimal.ZERO) { sum,row -> sum+row.amount }
  val amounts=dao.cashboxAmounts(aliases(normalized),unit)
  val operational=amounts.fold(BigDecimal.ZERO) { sum,value -> sum+value.toBigDecimal() }
  // A historical transaction is never inserted into the operational table by this projection.
  // Historical duplicate ids are excluded by LegacyCashboxHistory.
  val verified=false // Opening balances, closing carryover and cross-source overlap are not yet reconciled.
  return Result(historical,operational,historical+operational,verified,
   "المجموع صافي حركات فقط؛ لم تُطابق الأرصدة الافتتاحية والإغلاقات أو التداخل بين الأرشيف والجديد")
 }
}
