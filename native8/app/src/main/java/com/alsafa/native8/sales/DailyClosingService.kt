package com.alsafa.native8.sales

import androidx.room.withTransaction
import com.alsafa.native8.data.migration.LegacyCashboxHistory
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/**
 * Offline daily close: original counted values remain immutable; reconciliation and
 * drawer->external transfer are one atomic ledger transaction with the snapshot.
 * Missed days are carried forward by all intervening movements, never by replaying
 * the last snapshot as a new receipt.
 */
class DailyClosingService(private val db:AppDatabase) {
 private val currencies=listOf("USD","SYP","TRY")
 private val shamCurrencies=listOf("USD","SYP")
 data class Counts(
  val drawerActual:Map<String,BigDecimal>,
  val drawerTransfer:Map<String,BigDecimal>,
  val shamActual:Map<String,BigDecimal>,
  val notes:String=""
 )
 data class Preview(val drawerExpected:Map<String,BigDecimal>,val shamExpected:Map<String,BigDecimal>)
 private fun json(map:Map<String,BigDecimal>)=JSONObject().apply {
  map.forEach { (key,value)->put(key,value.stripTrailingZeros().toPlainString()) }
 }.toString()
 private fun value(json:String,currency:String):BigDecimal =
  JSONObject(json).optString(currency,"0").toBigDecimal()
 private fun today()=LocalDate.now(ZoneId.systemDefault()).toString()
 private suspend fun expected(vault:String,currency:String,prior:DailyClosingEntity?,now:Long):BigDecimal {
  if(prior==null) return UnifiedCashboxBalance(AppDatabase.cashContext(),db.supplierPurchaseDao())
   .calculate(vault,currency).total
  val priorValue=if(vault=="drawer") value(prior.drawer_next_json,currency)
   else value(prior.sham_actual_json,currency)
  val movementSum=db.supplierPurchaseDao().cashMovementsBetween(prior.created_at,now)
   .filter { UnifiedCashboxBalance.canonical(it.cashbox)==vault && it.currency.equals(currency,true) }
   .fold(BigDecimal.ZERO) { sum,row -> sum+row.amount.toBigDecimal() }
  return (priorValue+movementSum).max(BigDecimal.ZERO)
 }
 suspend fun preview():Preview {
  val date=today()
  val prior=db.dailyClosingDao().previous(date)
  val now=System.currentTimeMillis()
  return Preview(
   currencies.associateWith { expected("drawer",it,prior,now) },
   shamCurrencies.associateWith { expected("shamcash",it,prior,now) }
  )
 }
 suspend fun close(counts:Counts):DailyClosingEntity=db.withTransaction {
  val date=today()
  require(db.dailyClosingDao().byDate(date)==null) { "تم إغلاق هذا اليوم مسبقاً" }
  val historicalClose=LegacyCashboxHistory(AppDatabase.cashContext()).entries("dailyClosings",100000)
   .any { row -> runCatching { JSONObject(row.raw).optString("date")==date }.getOrDefault(false) }
  require(!historicalClose) { "يوجد إغلاق تاريخي لهذا اليوم؛ لا يمكن تكراره" }
  val now=System.currentTimeMillis()
  val prior=db.dailyClosingDao().previous(date)
  val drawerExpected=currencies.associateWith { expected("drawer",it,prior,now) }
  val shamExpected=shamCurrencies.associateWith { expected("shamcash",it,prior,now) }
  currencies.forEach { unit ->
   val actual=counts.drawerActual[unit] ?: error("المبلغ المعدود غير موجود: $unit")
   val transfer=counts.drawerTransfer[unit] ?: error("المبلغ المنقول غير موجود: $unit")
   require(actual>=BigDecimal.ZERO && transfer>=BigDecimal.ZERO && transfer<=actual) {
    "المبلغ المنقول يجب ألا يتجاوز المعدود: $unit"
   }
  }
  shamCurrencies.forEach { unit ->
   require((counts.shamActual[unit] ?: error("رصيد شام كاش غير موجود: $unit"))>=BigDecimal.ZERO)
  }
  val differences=currencies.associateWith { counts.drawerActual.getValue(it)-drawerExpected.getValue(it) }
  val shamDifferences=shamCurrencies.associateWith { counts.shamActual.getValue(it)-shamExpected.getValue(it) }
  val next=currencies.associateWith { counts.drawerActual.getValue(it)-counts.drawerTransfer.getValue(it) }
  val dao=db.supplierPurchaseDao()
  fun movementId(kind:String,currency:String)= "daily-close-$date-$kind-$currency"
  suspend fun post(kind:String,vault:String,currency:String,amount:BigDecimal,note:String) {
   if(amount.compareTo(BigDecimal.ZERO)!=0) dao.insertCashMovement(
    RoomCashboxMovement(movementId(kind,currency),vault,currency,
     amount.stripTrailingZeros().toPlainString(),"daily-close-$date",now,note))
  }
  for(unit in currencies) {
   post("drawer-reconcile","drawer",unit,differences.getValue(unit),"تسوية جرد الدرج")
   val transfer=counts.drawerTransfer.getValue(unit)
   post("drawer-out","drawer",unit,transfer.negate(),"إغلاق يومي — تحويل إلى الصندوق الخارجي")
   post("external-in","external",unit,transfer,"إغلاق يومي — تحويل من الدرج")
  }
  for(unit in shamCurrencies) {
   post("sham-reconcile","shamcash",unit,shamDifferences.getValue(unit),"تسوية جرد شام كاش")
  }
  DailyClosingEntity(date,now,json(counts.drawerActual),json(counts.drawerTransfer),
   json(next),json(differences),json(counts.shamActual),json(shamDifferences),counts.notes)
   .also { db.dailyClosingDao().insert(it) }
 }
}
