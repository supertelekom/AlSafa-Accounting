package com.alsafa.native8.sales

import java.math.BigDecimal
import java.math.RoundingMode

/** USD accounting values. Cash conversion is a separate, explicit snapshot. */
object SaleExchangeMath {
 private val z=BigDecimal.ZERO
 data class Settlement(val total:BigDecimal,val refund:BigDecimal,val extraDue:BigDecimal,val paid:BigDecimal,val due:BigDecimal)
 fun settle(total:BigDecimal,paid:BigDecimal,returned:BigDecimal,replacement:BigDecimal,collect:BigDecimal):Settlement {
  require(listOf(total,paid,returned,replacement,collect).all{it>=z}){"المبالغ لا تقبل قيماً سالبة"}
  require(paid<=total && returned<=total){"قيمة المرتجع أو المقبوض لا تطابق الفاتورة"}
  val next=total-returned+replacement
  val refund=(paid-next).max(z);val extra=(next-paid).max(z)
  require(collect<=extra){"المقبوض أكبر من فرق الاستبدال المستحق"}
  return Settlement(next,refund,extra,paid-refund+collect,extra-collect)
 }
 fun recognized(total:BigDecimal,cost:BigDecimal,paid:BigDecimal):BigDecimal =
  if(total.signum()==0)BigDecimal.ZERO else (total-cost).multiply(paid.min(total)).divide(total,2,RoundingMode.HALF_UP)
}
