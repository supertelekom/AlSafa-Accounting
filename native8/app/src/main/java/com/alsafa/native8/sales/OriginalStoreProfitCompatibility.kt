package com.alsafa.native8.sales
import java.math.BigDecimal

/** Old source: missing saleType is STORE. Project missing profit rows without posting anything. */
object OriginalStoreProfitCompatibility {
 data class Result(val events:List<StoreSaleProfitEvent>,val unresolved:List<String>,val adjustments:List<StoreProfitAdjustment> = emptyList(),val reviewedIds:Set<String> = emptySet())
 suspend fun read(dao:SalesFinancialAuditDao,until:Long):Result {
  val events=mutableListOf<StoreSaleProfitEvent>();val issues=mutableListOf<String>()
  val candidates=dao.unpostedStoreSales(until)
  val adjustments=mutableListOf<StoreProfitAdjustment>()
  for(sale in candidates) try {
   val lines=dao.compatibilityLines(sale.id)
   require(lines.isNotEmpty()) { "أصناف الفاتورة غير محفوظة" }

   val revenue=sale.total.toBigDecimal();require(revenue>=BigDecimal.ZERO)
   val cost=lines.fold(BigDecimal.ZERO) { total,line ->
    val qty=line.quantity.toBigDecimal();val unitCost=line.unit_cost.toBigDecimal()
    require(qty>BigDecimal.ZERO && unitCost>=BigDecimal.ZERO)
    total+qty*unitCost
   }
   val returns=dao.compatibilityReturns(sale.id)
   val existing=dao.compatibilityAdjustments(sale.id)
   require(returns.map { it.id }.distinct().size==returns.size && returns.all { r -> r.created_at>=sale.created_at && lines.any { it.id==r.line_id } }) { "سجل مرتجع غير متطابق" }
   for(line in lines) {
    val quantities=returns.filter { it.line_id==line.id }.map { it.quantity.toBigDecimal() }
    require(quantities.all { it>BigDecimal.ZERO })
    val sum=quantities.fold(BigDecimal.ZERO,BigDecimal::add)
    require(sum.compareTo(line.returned_qty.toBigDecimal())==0 && sum<=line.quantity.toBigDecimal()) { "كمية المرتجع لا تطابق سجلاته" }
   }
   // Mixing an existing adjustment with a reconstructed one needs explicit linkage.
   require(existing.isEmpty() || returns.isEmpty()) { "توجد تسويات محفوظة تحتاج مطابقة مع المرتجع لمنع التكرار" }
   val gross=lines.fold(BigDecimal.ZERO) { a,l -> a+l.quantity.toBigDecimal()*l.unit_price.toBigDecimal() }
   val pending=mutableListOf<StoreProfitAdjustment>()
   var delta=BigDecimal.ZERO
   val cancellations=returns.filter { it.reason=="إلغاء الفاتورة" }
   if(sale.status=="ملغاة") require(lines.all { it.returned_qty.toBigDecimal().compareTo(it.quantity.toBigDecimal())==0 }) { "الإلغاء لا يغطي كل الأصناف" }
   for(r in returns.filter { it.reason!="إلغاء الفاتورة" }) {
    require(gross>BigDecimal.ZERO)
    val line=lines.single { it.id==r.line_id };val qty=r.quantity.toBigDecimal()
    val refund=(line.unit_price.toBigDecimal()*qty*revenue).divide(gross,2,java.math.RoundingMode.HALF_UP)
    val effect=line.unit_cost.toBigDecimal()*qty-refund;delta+=effect
    pending+=StoreProfitAdjustment("compat-return:${r.id}",sale.id,r.created_at,effect.toPlainString(),"مرتجع: ${r.reason}")
   }
   if(cancellations.isNotEmpty()) {
    require(sale.status=="ملغاة") { "سجل إلغاء لفاتورة غير ملغاة" }
    val at=cancellations.maxOf { it.created_at }
    require(returns.filter { it.reason!="إلغاء الفاتورة" }.all { it.created_at<=at })
    pending+=StoreProfitAdjustment("compat-cancel:${sale.id}",sale.id,at,(-(revenue-cost+delta)).toPlainString(),"إلغاء فاتورة")
   } else if(sale.status=="ملغاة") require((revenue-cost+delta).signum()==0) { "تاريخ إلغاء الرصيد المتبقي غير محفوظ" }
   adjustments+=pending.filter { it.created_at<until }
   events+=StoreSaleProfitEvent(sale.id,sale.created_at,revenue.toPlainString(),cost.toPlainString(),(revenue-cost).toPlainString())
  } catch(e:Exception) { issues+="فاتورة رقم ${sale.number}: ${e.message}" }
  return Result(events,issues,adjustments,candidates.map { it.id }.toSet())
 }
}
