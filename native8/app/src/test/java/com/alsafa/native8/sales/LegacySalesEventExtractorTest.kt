package com.alsafa.native8.sales

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class LegacySalesEventExtractorTest {
 private fun row(json:String)=LegacySaleEntity("s1","sha",42,1,"","","USD","100","100","0","confirmed","",json)
 @Test fun storeProfitUsesOriginalFundingLots() {
  val json="""{"saleType":"store","date":"2026-10-20","currency":"USD","total":100,"tax":0,"rateSnapshot":{"USD":1},"items":[{"qty":2,"fundingAllocations":[{"qty":2,"unitCost":30,"currency":"USD"}]}]}"""
  val result=LegacySalesEventExtractor.extractStore(listOf(row(json)))
  assertTrue(result.unresolved.isEmpty())
  assertEquals(0,result.events.single().profitUsd.compareTo(BigDecimal("40")))
 }
 @Test fun missingHistoricalFundingCostIsNotGuessed() {
  val json="""{"saleType":"store","date":"2026-10-20","currency":"USD","total":100,"tax":0,"rateSnapshot":{"USD":1},"items":[{"qty":1}]}"""
  val result=LegacySalesEventExtractor.extractStore(listOf(row(json)))
  assertTrue(result.events.isEmpty())
  assertEquals(1,result.unresolved.size)
 }
 @Test fun onlineInvoiceCannotBeRecognizedOnSaleDate() {
  val json="""{"saleType":"online","date":"2026-10-20","currency":"USD","total":100,"tax":0,"rateSnapshot":{"USD":1},"items":[]}"""
  assertTrue(LegacySalesEventExtractor.extractStore(listOf(row(json))).events.isEmpty())
 }
}
