package com.alsafa.native8.sales
import org.junit.Assert.*
import org.junit.Test
class SalesSearchFieldsTest {
 @Test fun skipsPhotosAndNestedPayloads() {
  val f=SalesSearchFields.read("""{"name":"ساعة","stock":3,"photos":["${"A".repeat(2000000)}"],"metadata":{"name":"ignored"},"cost":null,"barcode":"123"}""")
  assertEquals(mapOf("name" to "ساعة","stock" to "3","barcode" to "123"),f)
 }
 @Test fun originalSearchFieldsAndBarcode() {
  for(key in listOf("name","model","brand","barcode","ref","reference","sku","id")) {
   assertTrue(SalesSearchFields.matches(mapOf(key to "ABC123")," bc1 "))
  }
  assertFalse(SalesSearchFields.matches(mapOf("name" to "ABC")," "))
  assertFalse(SalesSearchFields.matches(mapOf("barcode" to "1234"),"123",true))
  assertTrue(SalesSearchFields.matches(mapOf("barcode" to "1234"),"1234",true))
 }
}
