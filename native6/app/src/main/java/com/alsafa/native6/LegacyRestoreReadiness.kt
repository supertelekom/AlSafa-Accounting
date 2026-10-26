package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject

/**
 * Explicitly separates lossless archival import from financial activation.
 * A backup may be archived even if historical money needs reconciliation.
 */
object LegacyRestoreReadiness {
    fun inspect(payload:String):JSONObject {
        return inspectRoot(JSONObject(payload))
    }

    fun inspectRoot(root:JSONObject):JSONObject {
        require(root.optString("format")=="alsafa-accounting-backup" && root.optInt("formatVersion")==2) { "Unsupported legacy backup format" }
        val payment=LegacyPaymentLinkAudit.inspectRoot(root)
        val cash=LegacyPaymentCashAudit.inspectRoot(root)
        val supplier=LegacySupplierCashAudit.inspectRoot(root)
        val returns=LegacyReturnLinkAudit.inspectRoot(root)
        val closings=LegacyClosingContinuity.inspectRoot(root)
        val vaults=LegacyVaultSnapshot.inspectRoot(root)
        val financialWarnings=LegacyImportPreflight.inspectFinancialHistoryData(root.getJSONObject("data"))
        val blockers=JSONArray()
        fun flag(condition:Boolean,code:String) { if(condition) blockers.put(code) }
        flag(financialWarnings.isNotEmpty(),"FINANCIAL_HISTORY_INVALID")
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
        flag(vaults.getJSONArray("invalidMovementIndexes").length()>0,"VAULT_MOVEMENT_INVALID")
        flag(vaults.getJSONArray("duplicateMovementIndexes").length()>0,"VAULT_MOVEMENT_DUPLICATE")
        flag(vaults.getJSONArray("negativeFinalBalances").length()>0,"VAULT_NEGATIVE_BALANCE")
        flag(vaults.getJSONArray("fractionalMinorUnitRows").length()>0,"VAULT_FRACTIONAL_MINOR_UNITS")
        flag(closings.getJSONArray("arithmeticMismatches").length()>0,"CLOSING_ARITHMETIC_MISMATCH")
        flag(closings.getJSONArray("invalidDateRows").length()>0,"CLOSING_DATE_INVALID")
        flag(closings.getJSONArray("duplicateClosingDates").length()>0,"CLOSING_DATE_DUPLICATE")
        return JSONObject()
            .put("archiveImportPermitted",LegacyImportPreflight.inspectInventoryData(root.getJSONObject("data")).isEmpty())
            .put("financialActivationPermitted",false)
            .put("financialBlockers",blockers)
            .put("financialBlockerCount",blockers.length())
            .put("manualReconciliationRequired",true)
            .put("reason","Historical snapshot amounts and vault opening balances require independent reconciliation before financial activation")
            .put("posting","NONE")
    }
}
