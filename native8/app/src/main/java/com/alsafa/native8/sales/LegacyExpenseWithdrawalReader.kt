package com.alsafa.native8.sales

import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate

/** Original evidence is kept with each read-only financial row. No cash movements. */
object LegacyExpenseWithdrawalReader {
 data class Entry(val id:String,val date:String,val time:String,val amount:BigDecimal,
  val currency:String,val usdPerUnit:BigDecimal,val category:String,val paymentMethod:String,
  val vault:String,val notes:String,val partnerId:String?,val originalJson:String) {
  val money get()=LegacyFinancialRules.Money(amount,usdPerUnit)
 }
 data class Audit(val entries:List<Entry>,val unresolved:List<String>)
 fun extract(rows:List<JSONObject>,withdrawals:Boolean):Audit {
  val result=mutableListOf<Entry>();val issues=mutableListOf<String>()
  val included=if(withdrawals) rows.filter { it.optString("partnerType")!="external" } else rows
  val duplicates=included.groupBy { it.optString("id") }.filter { it.key.isBlank() || it.value.size>1 }.keys
  for(row in included) try {
   val id=row.getString("id");require(id !in duplicates) { "Missing/duplicate source ID" }
   val partner=if(withdrawals) row.getString("partnerId") else null
   require(!withdrawals || partner in LegacyFinancialRules.partners.map { it.id }) { "Unknown family partner: $partner" }
   val date=LocalDate.parse(row.getString("date")).toString()
   val amount=row.optString("amount").toBigDecimalOrNull() ?: error("Missing amount")
   require(amount>=BigDecimal.ZERO) { "Negative source amount" }
   val currency=row.getString("currency")
   val rate=LegacyFinancialExchange.usdPerUnit(currency,row.optJSONObject("rateSnapshot")?.optString(currency))
   result+=Entry(id,date,row.optString("time"),amount,currency,rate,row.optString("category"),
    row.optString("paymentMethod"),row.optString("vault"),row.optString("notes"),partner,row.toString())
  } catch(e:Exception) { issues+="${if(withdrawals) "Withdrawal" else "Expense"} ${row.optString("id")}: ${e.message}" }
  return Audit(result,issues.distinct())
 }
}
