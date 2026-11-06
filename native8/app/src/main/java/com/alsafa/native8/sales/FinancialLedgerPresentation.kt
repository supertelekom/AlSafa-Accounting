package com.alsafa.native8.sales

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

/** Source keys stay internal. Rows from every origin share one chronological presentation. */
object FinancialLedgerPresentation {
 data class Row(val key:String,val date:String,val title:String,val detail:String,val amount:BigDecimal,val kind:String)
 fun money(value:BigDecimal)=value.setScale(2,RoundingMode.HALF_UP).toPlainString()+" USD"
 fun render(rows:List<Row>):String {
  require(rows.map { it.key }.distinct().size==rows.size) { "تكرار عملية في تفاصيل الحساب" }
  return rows.sortedWith(compareBy({it.date},{it.key})).joinToString("\n\n") {
   "${it.date}\n${it.kind} — ${it.title}\n${it.detail}\n${money(it.amount)}"
  }.ifBlank { "لا توجد عمليات في الفترة المختارة" }
 }
 fun invoice(row:JSONObject):String {
  val number=row.optString("invoiceNo").ifBlank { row.optString("number") }
  return (if(number.isBlank()) "فاتورة دون رقم محفوظ" else "فاتورة رقم $number")+
   " — "+row.optString("customer").ifBlank { "عميل غير مسمى" }
 }
 fun description(row:JSONObject):String {
  val items=row.optJSONArray("items")
  return if(items!=null) (0 until items.length()).joinToString("، ") { i ->
   val item=items.getJSONObject(i)
   listOf(item.optString("name"),item.optString("model")).filter { it.isNotBlank() }.joinToString(" ")+" × "+item.optString("qty")
  } else listOf(row.optString("watch"),row.optString("quickServiceName"),row.optString("workDone")).filter { it.isNotBlank() }.distinct().joinToString(" — ")
 }
 fun index(sales:List<JSONObject>,repairs:List<JSONObject>,payments:List<JSONObject>,returns:List<JSONObject>):Map<String,JSONObject> {
  val result=mutableMapOf<String,JSONObject>()
  for(s in sales) {
   val id=s.getString("id");result["legacy:store:$id"]=s;result["legacy:online:$id:initial"]=s
   for(p in payments.filter { it.optString("referenceType")=="sale" && it.optString("referenceId")==id }) result["legacy:online:$id:payment:${p.getString("id")}"]=s
  }
  for(r in repairs) {
   val id=r.getString("id");result["legacy:repair:$id:initial"]=r;result["native:repair:$id:delivery"]=r
   for(p in payments.filter { it.optString("referenceType")=="repair" && it.optString("referenceId")==id }) {
    result["legacy:repair:$id:payment:${p.getString("id")}"]=r
    result["native:repair:$id:payment:${p.getString("id")}"]=r
   }
  }
  for(r in returns) {
   val repair=r.optString("referenceType")=="repair"
   val original=(if(repair)repairs else sales).firstOrNull { it.optString("id")==r.optString("referenceId") } ?: continue
   val suffix=if(repair) "repair-return" else "sale-return"
   result["legacy:$suffix:${r.getString("id")}"]=original
   result["native:$suffix:${r.getString("id")}"]=original
  }
  return result
 }
 fun event(event:FinancialSourceReconciliation.Event,index:Map<String,JSONObject>):Row {
  val original=index[event.sourceKey]
  val kind=when(event.stream) {
   FinancialSourceReconciliation.Stream.LEGACY_STORE -> "ربح مبيع محل"
   FinancialSourceReconciliation.Stream.LEGACY_ONLINE -> "ربح تحصيل أونلاين"
   FinancialSourceReconciliation.Stream.REPAIR -> "ربح صيانة"
   FinancialSourceReconciliation.Stream.RETURN -> "مرتجع أو تسوية"
   FinancialSourceReconciliation.Stream.EXPENSE -> "مصروف"
  }
  return Row(event.sourceKey,event.recognizedOn.toString(),original?.let(::invoice) ?: "تعذر العثور على تفاصيل الفاتورة",
   original?.let(::description).orEmpty(),event.profitUsd,kind)
 }
}
