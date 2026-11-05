package com.alsafa.native8.sales

import com.alsafa.native8.data.migration.LegacyReadRepository
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Read-only inventory of source payment rows. It deliberately does not infer
 * original FX, profit or payment dates from invoice totals.
 */
class LegacyPaymentEventAudit(private val source:LegacyReadRepository) {
 data class Result(val sourceRun:Long?,val eligible:List<Payment>,
  val unresolved:List<String>,val totalSourceRows:Int)
 data class Payment(
  val sourceKey:String,val saleId:String,val paidOn:LocalDate,
  val amount:BigDecimal,val currency:String
 )
 fun inspect():Result {
  val run=source.latestRunId() ?: return Result(null,emptyList(),emptyList(),0)
  var offset=0
  var total=0
  val eligible=mutableListOf<Payment>()
  val unresolved=mutableListOf<String>()
  val keys=mutableSetOf<String>()
  while(true) {
   val page=source.page(run,"payments",offset,500)
   total=page.total
   for(row in page.rows) {
    val obj=row.fields
    if(obj.optString("referenceType")!="sale") continue
    val key="import:$run:payments:${row.ordinal}"
    val saleId=obj.optString("referenceId")
    val date=obj.optString("date")
    val amount=obj.optString("amount").toBigDecimalOrNull()
    val currency=obj.optString("currency","USD").uppercase()
    val parsed=runCatching { LocalDate.parse(date) }.getOrNull()
    if(!keys.add(key) || saleId.isBlank() || parsed==null || amount==null ||
       amount<BigDecimal.ZERO || currency !in setOf("USD","TRY","SYP")) {
      unresolved+=key
    } else {
      eligible+=Payment(key,saleId,parsed,amount,currency)
    }
   }
   offset+=page.rows.size
   if(offset>=total) break
   check(page.rows.isNotEmpty()) { "Incomplete payments staging page" }
  }
  return Result(run,eligible,unresolved,total)
 }
}
