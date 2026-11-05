package com.alsafa.native8.sales

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class OnlineCapitalReportTest {
 private fun n(s:String)=BigDecimal(s)
 @Test fun stockCapitalIsIndependentOfCollections() {
  val line=RoomSaleLine("l","s","source","p",null,"watch","1","100","60","0")
  assertEquals(0,OnlineCapitalReport.stockCost(listOf(line)).compareTo(n("60")))
 }
 @Test fun partialCollectionsRecognizeOnlyProportionalCost() {
  val first=SaleProfitEvent("p1","s",1,"25","100","60","10")
  val second=SaleProfitEvent("p2","s",2,"25","100","60","10")
  assertEquals(0,OnlineCapitalReport.recognizedCost(listOf(first)).compareTo(n("15")))
  assertEquals(0,OnlineCapitalReport.recognizedCost(listOf(first,second)).compareTo(n("30")))
 }
 @Test(expected=IllegalArgumentException::class)
 fun negativeCostCannotAppearAsCapital() {
  val line=RoomSaleLine("l","s","source","p",null,"watch","1","100","-1","0")
  OnlineCapitalReport.stockCost(listOf(line))
 }
}
