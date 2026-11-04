package com.alsafa.native8.sales

import android.content.Context
import com.alsafa.native8.data.migration.LegacyCashboxHistory
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Historical cash is a read-only projection. Never re-post imported transactions to Room.
 * A daily closing is a snapshot, not an extra credit to add to earlier movements.
 */
class UnifiedCashboxBalance(private val context:Context,private val dao:SupplierPurchaseDao) {
 data class Result(val historical:BigDecimal,val operational:BigDecimal,
  val total:BigDecimal,val verified:Boolean,val warning:String)
 companion object {
  fun canonical(raw:String):String?=when(raw.trim().lowercase()) {
   "drawer","الدرج" -> "drawer"
   "external","الصندوق الخارجي" -> "external"
   "shamcash","sham","chamcash","شام كاش" -> "shamcash"
   "bank","البنك" -> "bank"
   else -> null
  }
  fun displayName(vault:String):String=when(canonical(vault)) {
   "drawer" -> "الدرج"
   "shamcash" -> "شام كاش"
   "external" -> "الصندوق الخارجي"
   "bank" -> "البنك"
   else -> vault
  }
  fun aliases(vault:String):List<String> = when(canonical(vault)) {
   "drawer" -> listOf("drawer","الدرج")
   "external" -> listOf("external","الصندوق الخارجي")
   "shamcash" -> listOf("shamcash","sham","chamcash","شام كاش")
   "bank" -> listOf("bank","البنك")
   else -> error("خزنة غير معروفة: "+vault)
  }
 }
  private fun jsonObject(value:Any?):JSONObject? {
   if(value is JSONObject) return value
   return try { JSONObject(value?.toString() ?: return null) } catch (_:Exception) { null }
  }
  suspend fun calculate(vault:String,currency:String):Result {
  val name=canonical(vault) ?: error("خزنة غير معروفة")
  val unit=currency.uppercase()
  val source=LegacyCashboxHistory(context)
  val history=source.cashMovements()
  val operationalIds=dao.operationalCashMovementIds().toSet()
  val overlapping=history.movements.filter { it.id in operationalIds }
  val historicalRows=history.movements.filter {
   canonical(it.vault)==name && it.currency.equals(unit,true) && it.id !in operationalIds
  }
  val entries=source.entries("dailyClosings",10000)
  val closings=entries.mapNotNull { row ->
   val obj=jsonObject(row.raw) ?: return@mapNotNull null
   obj.optLong("createdAt",0L).takeIf { it>0L }?.let { it to obj }
  }.sortedByDescending { it.first }
  // A native closing is the latest immutable snapshot for drawer / Sham Cash.
  // It must supersede older imported snapshots, without reposting its balance.
  val nativeClosing=if(name=="drawer" || name=="shamcash")
   AppDatabase.get(context).dailyClosingDao().recent().firstOrNull() else null
  val field=when(name) {
   "drawer" -> "drawerNext"
   "shamcash" -> "shamActual"
   else -> ""
  }
  val legacyAnchor=closings.firstNotNullOfOrNull { (time,obj) ->
   val values=jsonObject(obj.opt(field))
   val amount=values?.optString(unit)?.toBigDecimalOrNull()
   if(amount==null) null else time to amount
  }
  val nativeAmount=nativeClosing?.let { closing ->
   runCatching { JSONObject(if(name=="drawer") closing.drawer_next_json else closing.sham_actual_json)
    .optString(unit).toBigDecimalOrNull() }.getOrNull()
  }
  val anchor=if(nativeClosing!=null && nativeAmount!=null &&
   (legacyAnchor==null || nativeClosing.created_at>=legacyAnchor.first))
   nativeClosing.created_at to nativeAmount else legacyAnchor
  val createdAtById=source.entries("cashTransactions",100000).mapNotNull { entry ->
   val obj=jsonObject(entry.raw) ?: return@mapNotNull null
   obj.optString("id").takeIf { it.isNotBlank() }?.let { it to obj.optLong("createdAt",0L) }
  }.toMap()
  val old=if(anchor!=null) {
   val after=historicalRows.filter { row ->
    // Imported movement IDs are timestamped independently; use original row's createdAt.
    val created=createdAtById[row.id] ?: 0L
    created>anchor.first
   }
   anchor.second+after.fold(BigDecimal.ZERO) { sum,row -> sum+row.amount }
  } else historicalRows.fold(BigDecimal.ZERO) { sum,row -> sum+row.amount }
  val operational=if(anchor!=null) dao.cashMovementsBetween(anchor.first,Long.MAX_VALUE)
   .filter { canonical(it.cashbox)==name && it.currency.equals(unit,true) }
   .fold(BigDecimal.ZERO) { sum,row -> sum+row.amount.toBigDecimal() }
   else dao.cashboxAmounts(aliases(name),unit)
   .fold(BigDecimal.ZERO) { sum,value -> sum+value.toBigDecimal() }
  // For external and bank, full historical movement history needs an opening balance.
  val hasOpening=historicalRows.any { it.type=="opening-balance" }
  val complete=if(name=="drawer" || name=="shamcash") anchor!=null else hasOpening
  // Automated checks are not a substitute for a final physical cash audit.
  val verified=complete && history.invalid==0 && history.duplicates==0 && overlapping.isEmpty()
  val warning=when {
   overlapping.isNotEmpty() -> "تم استبعاد حركات متطابقة بالمعرف بين الأرشيف والجديد؛ يلزم تدقيق التداخل"
   history.invalid>0 || history.duplicates>0 -> "الأرشيف يحوي حركات ناقصة أو معرفات مكررة؛ الرصيد غير معتمد"
   !complete -> "لا يوجد رصيد إغلاق أو افتتاحي مؤكد لهذه الخزنة والعملة"
   else -> "رصيد محسوب من نقطة الترحيل والحركات؛ يُراجع على الجهاز قبل الاعتماد النهائي"
  }
  return Result(old,operational,old+operational,verified,warning)
 }
}
