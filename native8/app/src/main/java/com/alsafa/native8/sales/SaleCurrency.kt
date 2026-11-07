package com.alsafa.native8.sales
import java.util.Locale
object SaleCurrency {
 fun normalize(value:String):String=value.trim().uppercase(Locale.ROOT).also {
  require(it in setOf("USD","TRY","SYP")){"عملة غير مدعومة: $value"}
 }
}
