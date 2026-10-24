package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject

/**
 * Explicitly separates lossless archival import from financial activation.
 * A backup may be archived even if historical money needs reconciliation.
 */
object LegacyRestoreReadiness {
    fun inspect(payload:String):JSONObject {
        val payment=LegacyPaymentLinkAudit.inspect(payload)
        val cash=LegacyPaymentCashAudit.inspect(payload)
        val supplier=LegacySupplierCashAudit.inspect(payload)
        val returns=LegacyReturnLinkAudit.inspect(payload)
        val closings=LegacyClosingContinuity.inspect(payload)
        val blockers=JSONArray()
        fun flag(condition:Boolean,code:String) { if(condition) blockers.put(code) }
        flag(payment.getJSONArray("missingReferenceRows").length()>0,"PAYMENT_REFERENCE_MISSING")
        flag(payment.getJSONArray("unresolvedReferenceRows").length()>0,"PAYMENT_REFERENCE_UNKNOWN")
        flag(payment.getJSONArray("invalidAmountRows").length()>0,"PAYMENT_AMOUNT_INVALID")
        flag(payment.getJSONArray("invalidReceivedAmountRows").length()>0,"CASH_RECEIPT_AMOUNT_INVALID")
        flag(payment.getJSONArray("invalidPaymentCurrencyRows").length()>0,"PAYMENT_CURRENCY_INVALID")
        flag(payment.getJSONArray("duplicatePaymentIdRows").length()>0,"PAYMENT_ID_DUPLICATE")
        flag(cash.getJSONArray("unmatched").length()>0,"PAYMENT_CASH_UNMATCHED")
        flag(cash.getJSONArray("ambiguous").length()>0,"PAYMENT_CASH_AMBIGUOUS")
        flag(supplier.getJSONArray("unmatchedLedgerIndexes").length()>0,"SUPPLIER_CASH_UNMATCHED")
        flag(supplier.getJSONArray("ambiguousCandidates").length()>0,"SUPPLIER_CASH_AMBIGUOUS")
        flag(supplier.getJSONArray("invalidLedgerIndexes").length()>0,"SUPPLIER_LEDGER_INVALID")
        flag(returns.getJSONArray("missingReferenceRows").length()>0,"RETURN_REFERENCE_MISSING")
        flag(returns.getJSONArray("unresolvedReferenceRows").length()>0,"RETURN_REFERENCE_UNKNOWN")
        flag(returns.getJSONArray("duplicateReturnIdRows").length()>0,"RETURN_ID_DUPLICATE")
        flag(closings.getJSONArray("arithmeticMismatches").length()>0,"CLOSING_ARITHMETIC_MISMATCH")
        flag(closings.getJSONArray("invalidDateRows").length()>0,"CLOSING_DATE_INVALID")
        flag(closings.getJSONArray("duplicateClosingDates").length()>0,"CLOSING_DATE_DUPLICATE")
        return JSONObject()
            .put("archiveImportPermitted",true)
            .put("financialActivationPermitted",false)
            .put("financialBlockers",blockers)
            .put("financialBlockerCount",blockers.length())
            .put("manualReconciliationRequired",true)
            .put("reason","Historical snapshot amounts and vault opening balances require independent reconciliation before financial activation")
            .put("posting","NONE")
    }
}
