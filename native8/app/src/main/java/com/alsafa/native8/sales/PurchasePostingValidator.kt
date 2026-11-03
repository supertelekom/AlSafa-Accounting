package com.alsafa.native8.sales

import java.math.BigDecimal

/**
 * Pre-posting audit. No inventory, supplier or cash mutation is permitted here.
 * Historical supplier balances must never be replayed.
 */
object PurchasePostingValidator {
 data class Totals(val total:BigDecimal,val paid:BigDecimal,val remaining:BigDecimal)
 fun validate(
  draft:PurchaseDraftEntity,
  lines:List<PurchaseDraftLineEntity>,
  paidText:String,
  cashbox:String?
 ):Totals {
  require(draft.status=="DRAFT") { "الفاتورة معتمدة مسبقاً" }
  require(draft.supplier_id.isNotBlank()) { "اختر المورد" }
  require(draft.currency in setOf("USD","TRY","SYP")) { "عملة غير مدعومة" }
  require(lines.isNotEmpty()) { "الفاتورة بلا أصناف" }
  var total=BigDecimal.ZERO
  for(line in lines) {
   require(line.draft_id==draft.draft_id) { "صنف تابع لفاتورة أخرى" }
   require(line.product_id.isNotBlank() && line.product_source.isNotBlank()) { "معرف المنتج مفقود" }
   val qty=line.quantity_exact.toBigDecimal()
   val cost=line.unit_cost_exact.toBigDecimal()
   require(qty>BigDecimal.ZERO && cost>=BigDecimal.ZERO) { "كمية أو تكلفة غير صالحة" }
   total=total.add(qty.multiply(cost))
  }
  val paid=paidText.toBigDecimal()
  require(paid>=BigDecimal.ZERO && paid<=total) { "المدفوع خارج إجمالي الفاتورة" }
  require(paid==BigDecimal.ZERO || !cashbox.isNullOrBlank()) { "حدد صندوق الدفع" }
  return Totals(total,paid,total.subtract(paid))
 }
}
