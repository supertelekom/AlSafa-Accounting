package com.alsafa.native8.sales

import android.content.Context
import com.alsafa.native8.data.migration.LegacyCashboxHistory
import java.math.BigDecimal
import org.json.JSONObject

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
  fun aliases(vault:String):List<String> = when(canonical(vault)) {
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
  val historyAudit=LegacyCashboxHistory(context)
  // The last daily closing is an authoritative snapshot for drawer/shamcash.
  // Apply only subsequent cash movements, never replay the entire prior history.
  val closings=historyAudit.entries("dailyClosings",10000).mapNotNull { entry ->
   try { JSONObject(entry.raw) } catch (_:Exception) { null }
  }
  val latest=closings.maxByOrNull { it.optLong("createdAt",0L) }
  val anchor=when(normalized) {
   "drawer" -> latest?.optJSONObject("drawerNext")
   "shamcash" -> latest?.optJSONObject("shamActual")
   else -> null
  }
  val anchorTime=latest?.optLong("createdAt",0L) ?: 0L
  val anchorAmount=if(anchor!=null && anchor.has(unit) && !anchor.isNull(unit))
   anchor.optString(unit).toBigDecimalOrNull() else null
  val historical=if(anchorAmount!=null) {
   // Use createdAt from original staged JSON rather than ordinal or sync timestamp.
   val after=historyAudit.entries("cashTransactions",100000).mapNotNull { entry ->
    try {
     val obj=JSONObject(entry.raw)
     val id=obj.optString("id")
     if(old.none { it.id==id } || obj.optLong("createdAt",0L)<=anchorTime) null
     else obj.optString("amount").toBigDecimalOrNull()
    } catch (_:Exception) { null }
   }
   anchorAmount+after.fold(BigDecimal.ZERO,BigDecimal::add)
  } else old.fold(BigDecimal.ZERO) { sum,row -> sum+row.amount }
  val amounts=dao.cashboxAmounts(aliases(normalized),unit)
  val operational=amounts.fold(BigDecimal.ZERO) { sum,value -> sum+value.toBigDecimal() }
  // A historical transaction is never inserted into the operational table by this projection.
  // Historical duplicate ids are excluded by LegacyCashboxHistory.
  val verified=false // Cross-source duplicate detection and operational cash posting are not yet reconciled.
  return Result(historical,operational,historical+operational,verified,
   if(verified) "الرصيد التاريخي من الإغلاق المرحّل أو سجل الحركات، مضافاً إليه الحركات الجديدة؛ راجع أي تداخل بين المصادر" else "توجد بيانات ناقصة أو إغلاقات غير مكتملة؛ الرصيد يحتاج مراجعة")
 }
}
