package com.alsafa.native8.sales
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
class FinancialLedgerPresentationTest {
 @Test fun invoiceNumberCustomerAndItemsReplaceInternalStoreCode() {
  val sale=JSONObject("""{"id":"sale-x","invoiceNo":44,"customer":"عميل","items":[{"name":"CASIO","model":"MTP","qty":1}]}""")
  val index=FinancialLedgerPresentation.index(listOf(sale),emptyList(),emptyList(),emptyList())
  val e=FinancialSourceReconciliation.Event("legacy:store:sale-x",FinancialSourceReconciliation.Stream.LEGACY_STORE,LocalDate.parse("2026-09-22"),BigDecimal(30),BigDecimal(13))
  val result=FinancialLedgerPresentation.render(listOf(FinancialLedgerPresentation.event(e,index)))
  assertTrue(result.contains("فاتورة رقم 44"));assertTrue(result.contains("عميل"));assertTrue(result.contains("CASIO MTP"));assertTrue(result.contains("17.00 USD"));assertFalse(result.contains("legacy:"));assertFalse(result.contains("sale-x"))
 }
 @Test fun receiptsAndReturnsResolveTheirOriginalInvoice() {
  val sale=JSONObject("""{"id":"s","invoiceNo":77}""")
  val p=JSONObject("""{"id":"p","referenceId":"s","referenceType":"sale"}""")
  val r=JSONObject("""{"id":"r","referenceId":"s","referenceType":"sale"}""")
  val index=FinancialLedgerPresentation.index(listOf(sale),emptyList(),listOf(p),listOf(r))
  assertSame(sale,index["legacy:online:s:payment:p"]);assertSame(sale,index["legacy:sale-return:r"])
 }
 @Test fun everyOriginAndKindSharesChronologicalOrder() {
  fun row(key:String,date:String)=FinancialLedgerPresentation.Row(key,date,date,"",BigDecimal.ONE,"عملية")
  val result=FinancialLedgerPresentation.render(listOf(row("native:expense:1","2026-10-01"),row("legacy:store:1","2026-09-22"),row("native:withdrawal:1","2026-09-25")))
  assertTrue(result.indexOf("2026-09-22")<result.indexOf("2026-09-25"));assertTrue(result.indexOf("2026-09-25")<result.indexOf("2026-10-01"))
 }
 @Test(expected=IllegalArgumentException::class) fun duplicateEventCannotAppearTwice() {
  val row=FinancialLedgerPresentation.Row("x","2026-10-01","","",BigDecimal.ONE,"")
  FinancialLedgerPresentation.render(listOf(row,row))
 }
}
