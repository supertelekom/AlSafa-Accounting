package com.alsafa.native8.sales
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
class InvoiceMetadataEditTest {
 @Test fun preservesFinancialDataAndStableIdentity() {
  val original="""{"id":"sale-1","invoiceNo":7,"date":"2026-09-22","time":"09:00","total":95,"paid":50,"currency":"TRY","items":[{"id":"p1","qty":2}],"nativePayments":[{"amount":3}]}"""
  val edited=JSONObject(InvoiceMetadataEdit(8,"2026-09-23","10:30"," عميل ","123","ملاحظة").apply(original))
  for(key in listOf("id","total","paid","currency","items","nativePayments"))assertEquals(JSONObject(original).get(key).toString(),edited.get(key).toString())
  assertEquals("عميل",edited.getString("customer"));assertEquals(8,edited.getInt("invoiceNo"))
  val again=JSONObject(InvoiceMetadataEdit(9,"2026-09-24","11:00","عميل","123","").apply(edited.toString()))
  assertEquals(7,again.getJSONObject("nativeInvoiceMetadataOriginal").getInt("invoiceNo"))
 }
 @Test(expected=java.time.format.DateTimeParseException::class) fun invalidDateRejected(){InvoiceMetadataEdit(1,"wrong","09:00","","","").apply("{}")}
}
