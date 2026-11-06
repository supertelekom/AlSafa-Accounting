package com.alsafa.native8.sales

import com.alsafa.native8.data.migration.LegacyReadRepository
import java.math.BigDecimal
import org.json.JSONObject

/** Read-only historical list: original currency and date; no cash or stock postings. */
object LegacyFinancialHistoryDisplay {
 data class Entry(val date:String,val label:String,val amount:String,val currency:String,val details:String,val source:String)
 private fun amount(value:String):String =
  value.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: value
 fun legacy(reader:LegacyReadRepository,section:String):List<Entry> {
  val run=reader.latestRunId() ?: return emptyList()
  val entries=mutableListOf<Entry>()
  var offset=0
  while(true) {
   val page=reader.page(run,section,offset,500)
   for(row in page.rows) {
    val x=row.fields
    val label=if(section=="partnerWithdrawals") when(x.optString("partnerId")) {
     "father" -> "الأب"
     "me" -> "عبد الكريم"
     "brother" -> "يزن"
     else -> continue
    } else x.optString("category").ifBlank { x.optString("type","مصروف") }
    entries+=Entry(x.optString("date"),label,amount(x.optString("amount")),
     x.optString("currency","USD"),x.optString("notes"),
     "قديم")
   }
   offset+=page.rows.size
   if(offset>=page.total) break
   check(page.rows.isNotEmpty()) { "Missing historical $section records" }
  }
  return entries
 }
 fun render(entries:List<Entry>):String {
  if(entries.isEmpty()) return "لا توجد سجلات"
  return entries.sortedWith(compareByDescending<Entry> { it.date }.thenBy { it.label })
   .groupBy { it.date.take(7) }.entries.joinToString("\n\n") { (month,rows) ->
    "شهر $month\n"+rows.joinToString("\n") { e ->
     "${e.date}  |  ${e.label}  |  ${e.amount} ${e.currency}"+
      (if(e.details.isBlank()) "" else "  |  ${e.details}")
    }
   }
 }
}
