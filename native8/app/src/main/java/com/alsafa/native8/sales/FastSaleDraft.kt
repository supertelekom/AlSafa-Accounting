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
}
