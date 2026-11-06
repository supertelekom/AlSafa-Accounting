package com.alsafa.native8.home
import java.math.BigDecimal

/** Mirrors homeCurrencyAmountsHtml: positive receipts, kept in their original currency. */
class HomeIncomeTotals {
 private val seen=hashSetOf<String>()
 val sales=linkedMapOf<String,BigDecimal>()
 val repairs=linkedMapOf<String,BigDecimal>()
 fun add(id:String,kind:String,currency:String,amount:BigDecimal) {
  val target=when(kind){"sale"->sales;"repair"->repairs;else->return}
  if(!seen.add(id))return
  target[currency]=(target[currency] ?: BigDecimal.ZERO)+amount.max(BigDecimal.ZERO)
 }
}
