package com.alsafa.native8.sales

/** Explicit cashbox routing for operational expenses; unknown methods post no cash. */
object ExpensePaymentVault {
 fun route(method:String):String? {
  val value=method.trim().lowercase()
  return when {
   value=="external" || value.contains("خارجي") -> "external"
   value=="shamcash" || value=="chamcash" || value.contains("شام") -> "shamcash"
   value=="bank" || value.contains("بنك") -> "bank"
   value=="cash" || value=="drawer" || value.contains("نقد") -> "drawer"
   else -> null
  }
 }
}
