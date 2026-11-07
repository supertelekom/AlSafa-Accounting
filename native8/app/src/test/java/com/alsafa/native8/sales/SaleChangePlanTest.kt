package com.alsafa.native8.sales
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
class SaleChangePlanTest {
 private fun p(amount:String,unit:String="USD",rate:String="1",box:String="drawer"):SalePayment {
  val a=amount.toBigDecimal();val r=rate.toBigDecimal();return SalePayment(box,unit,a,"USD",r,(a*r).setScale(2,java.math.RoundingMode.HALF_UP))
 }
 private fun eq(want:String,value:BigDecimal)=assertEquals(0,want.toBigDecimal().compareTo(value))
 @Test fun currencyCase(){assertEquals("USD",SaleCurrency.normalize(" usd "));assertEquals("TRY",SaleCurrency.normalize("try"))}
 @Test fun hundredForNinetyFive(){val plan=SaleChangePlan.build("95".toBigDecimal(),listOf(p("100")),"external","TRY","0.02".toBigDecimal());eq("95",plan.receipts.single().amount);eq("5",plan.excess.single().amount);eq("250",plan.change!!.amount);assertEquals("external",plan.change.cashbox)}
 @Test fun mixedTender(){val plan=SaleChangePlan.build("55".toBigDecimal(),listOf(p("50"),p("500","TRY","0.02")),"drawer","USD",BigDecimal.ONE);eq("50",plan.receipts[0].amount);eq("250",plan.receipts[1].amount);eq("250",plan.excess.single().amount);eq("5",plan.change!!.amount)}
 @Test fun noExtra(){val plan=SaleChangePlan.build("95".toBigDecimal(),listOf(p("50")),null,null,null);assertNull(plan.change);eq("50",plan.receipts.single().amount)}
 @Test fun zeroInvoice(){val plan=SaleChangePlan.build(BigDecimal.ZERO,listOf(p("10")),"drawer","USD",BigDecimal.ONE);assertTrue(plan.receipts.isEmpty());eq("10",plan.change!!.amount)}
 @Test(expected=IllegalArgumentException::class) fun missingDestination(){SaleChangePlan.build("5".toBigDecimal(),listOf(p("10")),null,null,null)}
 @Test(expected=IllegalArgumentException::class) fun invalidShamTry(){SaleChangePlan.build("5".toBigDecimal(),listOf(p("10")),"shamcash","TRY","0.02".toBigDecimal())}
 @Test(expected=IllegalArgumentException::class) fun cannotRoundAwayMoney(){SaleChangePlan.build("9.99".toBigDecimal(),listOf(p("10")),"drawer","SYP","2".toBigDecimal())}
 @Test fun changeNotRefundableAgain(){val plan=SaleChangePlan.build("95".toBigDecimal(),listOf(p("100")),"external","TRY","0.02".toBigDecimal());eq("95",plan.receipts.fold(BigDecimal.ZERO){a,p->a+p.creditedAmount!!});eq("100",plan.receipts.sumOf{it.amount}+plan.excess.sumOf{it.amount})}
}
