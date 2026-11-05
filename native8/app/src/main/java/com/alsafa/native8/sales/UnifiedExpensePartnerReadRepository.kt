package com.alsafa.native8.sales

import java.math.BigDecimal
import java.time.LocalDate

/** Read-only union. Expenses are deducted once; withdrawals affect partner balances only. */
class UnifiedExpensePartnerReadRepository(private val legacy:HistoricalFinancialSource,private val dao:OperationalExpensePartnerDao) {
 data class Snapshot(val source:LegacyFinancialRules.Source,
  val legacyExpenses:Int,val operationalExpenses:Int,val legacyWithdrawals:Int,val operationalWithdrawals:Int,
  val unresolved:List<String> = emptyList(),
  val historicalEvidence:List<LegacyExpenseWithdrawalReader.Entry> = emptyList(),val importRunId:Long?=null)
 suspend fun load(from:String,to:String,allowIncomplete:Boolean=false):Snapshot {
  require(!LocalDate.parse(to).isBefore(LocalDate.parse(from)))
  val run=legacy.latestRunId()
  val expenseRows=if(run==null) emptyList() else legacy.financialRows(run,"expenses")
  val withdrawalRows=if(run==null) emptyList() else legacy.financialRows(run,"partnerWithdrawals")
  val oldExpenses=LegacyExpenseWithdrawalReader.extract(expenseRows,false)
  val oldWithdrawals=LegacyExpenseWithdrawalReader.extract(withdrawalRows,true)
  val issues=(oldExpenses.unresolved+oldWithdrawals.unresolved).toMutableList()
  val nativeExpenses=dao.expenses(from,to);val nativeWithdrawals=dao.withdrawals(from,to)
  val expenseOverlap=nativeExpenses.map { it.id }.toSet().intersect(expenseRows.map { it.optString("id") }.toSet())
  val withdrawalOverlap=nativeWithdrawals.map { it.id }.toSet().intersect(withdrawalRows.map { it.optString("id") }.toSet())
  issues.addAll(expenseOverlap.map { "Expense ID overlaps imported history: $it" })
  issues.addAll(withdrawalOverlap.map { "Withdrawal ID overlaps imported history: $it" })
  fun inPeriod(date:String)=date>=from && date<=to
  val oldExpenseEntries=oldExpenses.entries.filter { inPeriod(it.date) && it.id !in expenseOverlap }
  val oldWithdrawalEntries=oldWithdrawals.entries.filter { inPeriod(it.date) && it.id !in withdrawalOverlap }
  val expenses=oldExpenseEntries.map { LegacyFinancialRules.Expense(it.date,it.money) }.toMutableList()
  val withdrawals=oldWithdrawalEntries.map { LegacyFinancialRules.Withdrawal(it.date,requireNotNull(it.partnerId),it.money) }.toMutableList()
  fun money(amount:String,rate:String,currency:String):LegacyFinancialRules.Money {
   require(currency in setOf("USD","TRY","SYP")) { "Unsupported currency" }
   val value=amount.toBigDecimal();val fx=rate.toBigDecimal()
   require(value>=BigDecimal.ZERO && fx>BigDecimal.ZERO)
   require(currency!="USD" || fx.compareTo(BigDecimal.ONE)==0) { "USD exchange must be one" }
   return LegacyFinancialRules.Money(value,fx)
  }
  var newExpenses=0;var newWithdrawals=0
  for(row in nativeExpenses) try {
   require(row.id.isNotBlank() && row.id !in expenseOverlap)
   LocalDate.parse(row.date)
   expenses+=LegacyFinancialRules.Expense(row.date,money(row.amount,row.usd_per_unit,row.currency));newExpenses++
  } catch(e:Exception) { issues+="Expense ${row.id}: ${e.message}" }
  for(row in nativeWithdrawals) try {
   require(row.id.isNotBlank() && row.id !in withdrawalOverlap)
   require(row.partner_id in LegacyFinancialRules.partners.map { it.id }) { "Unknown partner" }
   LocalDate.parse(row.date)
   withdrawals+=LegacyFinancialRules.Withdrawal(row.date,row.partner_id,money(row.amount,row.usd_per_unit,row.currency));newWithdrawals++
  } catch(e:Exception) { issues+="Withdrawal ${row.id}: ${e.message}" }
  check(legacy.latestRunId()==run) { "Historical source changed during read" }
  if(!allowIncomplete) check(issues.isEmpty()) { issues.joinToString("\n") }
  return Snapshot(LegacyFinancialRules.Source(emptyList(),emptyList(),emptyList(),emptyList(),expenses,withdrawals),
   oldExpenseEntries.size,newExpenses,oldWithdrawalEntries.size,newWithdrawals,issues.distinct(),oldExpenseEntries+oldWithdrawalEntries,run)
 }
}
