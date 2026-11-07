package com.alsafa.native8.sales

import java.math.BigDecimal
import java.math.RoundingMode

/** Split tender into invoice receipts and an explicitly linked cash exchange.
 * Returns/cancellation reverse only the invoice receipts, never money already given as change.
 */
object SaleChangePlan {
 data class Plan(val receipts:List<SalePayment>,val excess:List<SalePayment>,val change:SalePayment?)
 private val zero=BigDecimal.ZERO
 fun build(net:BigDecimal,tender:List<SalePayment>,box:String?,currency:String?,rate:BigDecimal?):Plan {
  require(net>=zero){"إجمالي غير صالح"}
  tender.forEach { p ->
   require(p.amount>zero && p.invoiceCurrency=="USD" && p.fxRate!=null && p.fxRate>zero && p.creditedAmount!=null && p.creditedAmount>zero){"بيانات القبض غير مكتملة"}
   require(SaleCurrency.normalize(p.currency)==p.currency && p.cashbox in setOf("drawer","external","shamcash","bank")){"اختر صندوقاً وعملة صالحين"}
   require(p.creditedAmount.compareTo((p.amount*p.fxRate).setScale(2,RoundingMode.HALF_UP))==0){"المبلغ لا يطابق سعر الصرف"}
  }
  val paid=tender.fold(zero){a,p->a+p.creditedAmount!!}
  if(paid<=net)return Plan(tender,emptyList(),null)
  require(box in setOf("drawer","external","shamcash","bank") && currency in setOf("USD","TRY","SYP") && rate!=null && rate>zero){"اختر صندوق وعملة الباقي"}
  require(box!="shamcash"||currency!="TRY"){"شام كاش لا يدعم التركية"}
  val extra=paid-net
  fun cash(base:BigDecimal,fx:BigDecimal,unit:String):BigDecimal {
   val value=base.divide(fx,if(unit=="SYP")0 else 2,RoundingMode.HALF_UP)
   require(value>zero && (value*fx).setScale(2,RoundingMode.HALF_UP).compareTo(base)==0){"يوجد فرق تقريب؛ اختر عملة أخرى للباقي أو عدّل المبلغ"}
   return value
  }
  val receipts=mutableListOf<SalePayment>();val excess=mutableListOf<SalePayment>();var left=net
  for(p in tender){val credit=p.creditedAmount!!;val keep=left.min(credit)
   val amount=when {keep==zero->zero;keep.compareTo(credit)==0->p.amount;else->cash(keep,p.fxRate!!,p.currency)}
   if(keep>zero)receipts+=p.copy(amount=amount,creditedAmount=keep)
   if(credit>keep){val remainder=p.amount-amount
    require((remainder*p.fxRate!!).setScale(2,RoundingMode.HALF_UP).compareTo(credit-keep)==0){"فرق تقريب في تقسيم الدفعة"}
    excess+=p.copy(amount=remainder,creditedAmount=credit-keep)
   }
   left-=keep
  }
  return Plan(receipts,excess,SalePayment(box!!,currency!!,cash(extra,rate!!,currency),"USD",rate,extra))
 }
}
