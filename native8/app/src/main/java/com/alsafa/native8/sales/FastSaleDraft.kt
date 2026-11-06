package com.alsafa.native8.sales
import java.math.BigDecimal
import java.math.RoundingMode
object FastSaleDraft {
 fun create(source:String,id:String,variant:String?,title:String,cost:BigDecimal,costCurrency:String,amount:BigDecimal,currency:String,method:String,rate:(String,String)->BigDecimal):SaleDraft {
  require(amount>BigDecimal.ZERO && cost>=BigDecimal.ZERO)
  require(currency in listOf("USD","TRY","SYP"))
  val vault=when(method){"نقدي"->"drawer";"شام كاش"->"shamcash";else->error("طريقة دفع غير مدعومة")}
  require(vault!="shamcash" || currency!="TRY") { "شام كاش لا يدعم الليرة التركية" }
  val fx=rate(currency,"USD");val total=(amount*fx).setScale(2,RoundingMode.HALF_UP)
  require(total>BigDecimal.ZERO) { "المبلغ أقل من دقة الفاتورة" }
  return SaleDraft("","",listOf(SaleLine(source,id,variant,title,BigDecimal.ONE,total,cost*rate(costCurrency,"USD"))),listOf(SalePayment(vault,currency,amount,"USD",fx,total)),BigDecimal.ZERO,SalesChannelClassification.STORE)
 }
 data class Item(val source:String,val id:String,val variant:String?,val title:String,val cost:BigDecimal,val price:BigDecimal,val currency:String)
 fun createMany(items:List<Item>,amount:BigDecimal,currency:String,method:String,rate:(String,String)->BigDecimal):SaleDraft {
  require(items.isNotEmpty()) { "اختر الساعة أولاً" }
  val first=items.first()
  val template=create(first.source,first.id,first.variant,first.title,first.cost,first.currency,amount,currency,method,rate)
  val total=template.payments.single().creditedAmount!!
  val weights=items.map { require(it.cost>=BigDecimal.ZERO && it.price>=BigDecimal.ZERO);it.price*rate(it.currency,"USD") }
  val sum=weights.fold(BigDecimal.ZERO,BigDecimal::add)
  require(sum>BigDecimal.ZERO || items.size==1) { "حدد أسعار الأصناف قبل حفظ الفاتورة" }
  var allocated=BigDecimal.ZERO
  val lines=items.mapIndexed { i,item ->
   val value=if(i==items.lastIndex) total-allocated else total.multiply(weights[i]).divide(sum,2,RoundingMode.DOWN)
   allocated+=value
   SaleLine(item.source,item.id,item.variant,item.title,BigDecimal.ONE,value,item.cost*rate(item.currency,"USD"))
  }
  return template.copy(lines=lines)
 }
}
