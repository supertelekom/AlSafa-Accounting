package com.alsafa.native8.sales

import java.math.BigDecimal

/**
 * Read-only accounting review. Never add historical ledger amounts to invoice
 * dues: a ledger payment may already be included in the imported paid snapshot.
 */
class SupplierBalanceReview(private val dao: SupplierPurchaseDao) {
 data class Invoice(val number:String,val currency:String,val total:BigDecimal,
                    val paid:BigDecimal,val remaining:BigDecimal)
 data class Ledger(val currency:String,val opening:BigDecimal,
                   val payments:BigDecimal,val unknownKinds:Set<String>)
 data class Result(val invoices:List<Invoice>,val ledger:List<Ledger>,
                   val warnings:List<String>,val approvedForPosting:Boolean=false)

 suspend fun inspect(supplierId:String):Result {
  require(supplierId.isNotBlank())
  val warnings=mutableListOf<String>()
  val invoices=dao.purchases(supplierId).map { row ->
   val total=row.total_exact.toBigDecimalOrNull()
   val paid=row.paid_at_import_exact.toBigDecimalOrNull()
   if(total==null || paid==null || total.signum()<0 || paid.signum()<0 || paid>total) {
    warnings+="Invalid imported invoice amount: ${row.archive_key}"
    Invoice(row.invoice_number,row.currency,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO)
   } else {
    Invoice(row.invoice_number,row.currency,total,paid,total.subtract(paid))
   }
  }
  val ledger=dao.historicalLedger(supplierId).groupBy { it.currency }.map { (currency, rows) ->
   var opening=BigDecimal.ZERO
   var payments=BigDecimal.ZERO
   val unknown=linkedSetOf<String>()
   rows.forEach { row ->
    val amount=row.amount_exact.toBigDecimalOrNull()
    if(amount==null || amount.signum()<0) {
     warnings+="Invalid historical ledger amount: ${row.archive_key}"
    } else when(row.kind) {
     "opening-debt" -> opening=opening.add(amount)
     "payment" -> payments=payments.add(amount)
     else -> unknown.add(row.kind)
    }
   }
   if(unknown.isNotEmpty()) warnings+="Unknown ledger kinds for $currency: $unknown"
   Ledger(currency,opening,payments,unknown)
  }
  val invoiceCurrencies=invoices.map { it.currency }.toSet()
  val ledgerCurrencies=ledger.map { it.currency }.toSet()
  if(invoiceCurrencies.intersect(ledgerCurrencies).isNotEmpty()) {
   warnings+="Potential overlap: imported invoice paid amounts may already include ledger payments"
  }
  return Result(invoices,ledger,warnings)
 }
}
