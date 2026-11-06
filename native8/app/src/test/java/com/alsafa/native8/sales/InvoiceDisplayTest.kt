package com.alsafa.native8.sales
import org.junit.Test
import org.junit.Assert.*
class InvoiceDisplayTest {
 @Test fun formatsOnlyTheDisplayedValue(){
  assertEquals("100",InvoiceDisplay.amount("100.000000000000000"))
  assertEquals("1.24",InvoiceDisplay.amount("1.235"))
  assertEquals("-5.2",InvoiceDisplay.amount("-5.20000"))
  assertEquals("0",InvoiceDisplay.amount("0.000000"))
  assertEquals("—",InvoiceDisplay.amount("—"))
 }
 @Test fun phoneSupportsArabicDigitsAndMissingNumber(){
  assertEquals("",InvoiceDisplay.phone("  "))
  assertEquals("90555123",InvoiceDisplay.phone("+٩٠ ٥٥٥-١٢٣"))
 }
}
