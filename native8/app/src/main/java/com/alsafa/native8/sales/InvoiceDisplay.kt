package com.alsafa.native8.sales

import java.math.BigDecimal
import java.math.RoundingMode

/** Presentation only: never change persisted amounts or calculation precision. */
object InvoiceDisplay {
 fun amount(value:String):String=value.toBigDecimalOrNull()?.setScale(2,RoundingMode.HALF_UP)?.stripTrailingZeros()?.toPlainString() ?: value
 fun amount(value:BigDecimal):String=amount(value.toPlainString())
 fun phone(value:String):String=value.filter{it.isDigit()}.map{Character.digit(it,10)}.joinToString("")
}
