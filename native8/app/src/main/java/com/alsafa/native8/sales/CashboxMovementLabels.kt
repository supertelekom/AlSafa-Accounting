package com.alsafa.native8.sales

/** UI-only labels for stored ledger codes. Never rewrite source identifiers or amounts. */
object CashboxMovementLabels {
 fun vault(value:String):String=UnifiedCashboxBalance.displayName(value)
 fun currency(value:String):String=when(value.uppercase()) {
  "USD" -> "دولار"
  "TRY" -> "ليرة تركية"
  "SYP" -> "ليرة سورية"
  else -> value
 }
 fun type(value:String):String=when(value.trim().lowercase()) {
  "transfer","cash-transfer","vault-transfer" -> "تحويل بين الخزائن"
  "transfer-in" -> "تحويل وارد"
  "transfer-out" -> "تحويل صادر"
  "expense","operating-expense" -> "مصروف"
  "partner-withdrawal","partner_withdrawal" -> "سحب شريك"
  "opening-balance" -> "رصيد افتتاحي"
  "sale","sale-payment","sales-payment" -> "تحصيل مبيع"
  "repair","maintenance","repair-payment" -> "تحصيل صيانة"
  "receivable","receivable-collection" -> "تحصيل آجل"
  "cash-exchange","exchange" -> "صرف عملة"
  "daily-close","daily-closing" -> "إغلاق يومي"
  else -> value
 }
 fun note(value:String):String {
  var result=value
  val names=listOf("drawer","external","shamcash","chamcash","sham","bank")
  for(code in names) {
   val label=vault(code)
   result=result.replace(Regex("(?i)(?<![a-zA-Z])"+Regex.escape(code)+"(?![a-zA-Z])"),label)
  }
  return result
 }
 fun operational(note:String,reference:String):String {
  val cleaned=note(note)
  return if(cleaned.isNotBlank()) cleaned else when {
   reference.startsWith("cash-transfer-") -> "تحويل بين الخزائن"
   reference.startsWith("repair-") -> "تحصيل صيانة"
   else -> "حركة مالية"
  }
 }
}
