package com.alsafa.native8.sales

import com.alsafa.native8.data.migration.LegacyReadRepository
import java.math.BigDecimal

/**
 * Read-only union of imported and newly entered expenses/partner withdrawals.
 * Does not post imported movements into the operational cashbox ledger.
 * Missing exchange snapshots are rejected rather than silently assumed.
 */
class UnifiedExpensePartnerReadRepository(
 private val legacy:LegacyReadRepository,
 private val dao:OperationalExpensePartnerDao
) {
 data class Snapshot(val source:LegacyFinancialRules.Source,
  val legacyExpenses:Int,val operationalExpenses:Int,
  val legacyWithdrawals:Int,val operationalWithdrawals:Int)
 private fun decimal(value:String):BigDecimal =
  value.toBigDecimalOrNull() ?: error("قيمة مالية غير صالحة: $value")
 private fun money(amount:String,rate:String):LegacyFinancialRules.Money =
  LegacyFinancialRules.Money(decimal(amount),decimal(rate))
 private fun rows(run:Long,section:String):List<LegacyReadRepository.LegacyRow> {
  val result=mutableListOf<LegacyReadRepository.LegacyRow>()
  var offset=0
  while(true) {
   val page=legacy.page(run,section,offset,500)
   result.addAll(page.rows)
   offset+=page.rows.size
   if(offset>=page.total) return result
   check(page.rows.isNotEmpty()) { "Incomplete legacy section $section" }
  }
 }
 private fun exchangeSnapshot(raw:org.json.JSONObject,currency:String):BigDecimal {
  if(currency=="USD") return BigDecimal.ONE
  val snapshot=raw.optJSONObject("rateSnapshot")
   ?: error("Missing historical rateSnapshot for $currency")
  return LegacyFinancialExchange.usdPerUnit(currency,snapshot.optString(currency))
 }
 suspend fun load(from:String,to:String):Snapshot {
  require(from<=to)
  val run=legacy.latestRunId()
  val importedExpenseRows=if(run==null) emptyList() else rows(run,"expenses")
  val importedWithdrawalRows=if(run==null) emptyList() else rows(run,"partnerWithdrawals")
  val legacyExpenses=importedExpenseRows.map { row ->
   val obj=row.fields
   val unit=obj.optString("currency","USD").uppercase()
   val date=obj.optString("date")
   require(date.isNotBlank()) { "Expense date missing" }
   LegacyFinancialRules.Expense(date,LegacyFinancialRules.Money(
    decimal(obj.get("amount").toString()),exchangeSnapshot(obj,unit)))
  }.filter { LegacyFinancialRules.inRange(it.date,from,to) }
  val legacyWithdrawals=importedWithdrawalRows.map { row ->
   val obj=row.fields
   val unit=obj.optString("currency","USD").uppercase()
   val date=obj.optString("date")
   require(date.isNotBlank()) { "Withdrawal date missing" }
   LegacyFinancialRules.Withdrawal(date,obj.optString("partnerId"),
    LegacyFinancialRules.Money(decimal(obj.get("amount").toString()),
     exchangeSnapshot(obj,unit)))
  }.filter { LegacyFinancialRules.inRange(it.date,from,to) }
  val operationalExpenseRows=dao.expenses(from,to)
  val operationalWithdrawalRows=dao.withdrawals(from,to)
  val expenseIds=importedExpenseRows.mapNotNull { it.id }.toSet()
  val withdrawalIds=importedWithdrawalRows.mapNotNull { it.id }.toSet()
  check(operationalExpenseRows.none { it.id in expenseIds }) { "Expense ID overlaps imported history" }
  check(operationalWithdrawalRows.none { it.id in withdrawalIds }) { "Withdrawal ID overlaps imported history" }
  val newExpenses=operationalExpenseRows.map {
   LegacyFinancialRules.Expense(it.date,money(it.amount,it.usd_per_unit))
  }
  val newWithdrawals=operationalWithdrawalRows.map {
   LegacyFinancialRules.Withdrawal(it.date,it.partner_id,money(it.amount,it.usd_per_unit))
  }
  return Snapshot(LegacyFinancialRules.Source(
   emptyList(),emptyList(),emptyList(),emptyList(),
   legacyExpenses+newExpenses,legacyWithdrawals+newWithdrawals),
   legacyExpenses.size,newExpenses.size,legacyWithdrawals.size,newWithdrawals.size)
 }
}
