package com.alsafa.native8.sales
import java.math.BigDecimal

/** Old source: missing saleType is STORE. Project missing profit rows without posting anything. */
object OriginalStoreProfitCompatibility {
 data class Result(val events:List<StoreSaleProfitEvent>,val unresolved:List<String>)
 suspend fun read(dao:SalesFinancialAuditDao,until:Long):Result {
  val events=mutableListOf<StoreSaleProfitEvent>();val issues=mutableListOf<String>()
  for(sale in dao.unpostedStoreSales(until)) try {
   val lines=dao.compatibilityLines(sale.id)
   require(lines.isNotEmpty()) { "أصناف الفاتورة غير محفوظة" }
   require(sale.status!="ملغاة" && lines.all { it.returned_qty.toBigDecimal().signum()==0 }) { "يلزم سجل المرتجع أو الإلغاء لتحديد أثره بتاريخ العملية" }
   val revenue=sale.total.toBigDecimal();require(revenue>=BigDecimal.ZERO)
   val cost=lines.fold(BigDecimal.ZERO) { total,line ->
    val qty=line.quantity.toBigDecimal();val unitCost=line.unit_cost.toBigDecimal()
    require(qty>BigDecimal.ZERO && unitCost>=BigDecimal.ZERO)
    total+qty*unitCost
   }
   events+=StoreSaleProfitEvent(sale.id,sale.created_at,revenue.toPlainString(),cost.toPlainString(),(revenue-cost).toPlainString())
  } catch(e:Exception) { issues+="فاتورة رقم ${sale.number}: ${e.message}" }
  return Result(events,issues)
 }
}
