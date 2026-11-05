package com.alsafa.native8.sales

import com.alsafa.native8.data.migration.LegacyReadRepository

/** One source-run census before financial approval, without ledger writes. */
class LegacyFinancialSourceAudit(private val legacy:LegacyReadRepository) {
 data class Section(val name:String,val count:Int)
 data class Result(val runId:Long?,val sections:List<Section>,
  val paymentIssues:Int,val datedSalePayments:Int) {
  val complete:Boolean get()=false // Counts are not a substitute for source parity.
 }
 fun inspect():Result {
  val run=legacy.latestRunId() ?: return Result(null,emptyList(),0,0)
  val sections=listOf("sales","payments","repairs","expenses","partnerWithdrawals")
   .map { Section(it,legacy.page(run,it,0,1).total) }
  val payments=LegacyPaymentEventAudit(legacy).inspect()
  return Result(run,sections,payments.unresolved.size,payments.eligible.size)
 }
}
