package com.alsafa.native6

import org.json.JSONObject

/**
 * Read-only gate for a future full financial migration.
 *
 * A restored inventory snapshot is not an activated financial ledger. Do not
 * insert old invoices, receipts or cash balances into native tables until a
 * single atomic migration can reconcile all linked records and opening vaults.
 */
object LegacyFinancialActivationGate {
    fun inspect(store: Store): JSONObject {
        val raw=store.rows("SELECT value FROM settings WHERE key=?", "legacy_snapshot_import")
            .firstOrNull()?.get("value").orEmpty()
        if(raw.isBlank()) return JSONObject()
            .put("state","NO_LEGACY_IMPORT")
            .put("financialActive",true)
        val meta=JSONObject(raw)
        val status=meta.optString("financialPostingStatus","NOT_POSTED")
        val cashVerified=meta.optBoolean("cashContinuityVerified",false)
        val journalCount=store.scalar("SELECT count(*) FROM journal")
        val invoiceCount=store.scalar("SELECT count(*) FROM invoices")
        val legacyCount=store.scalar("SELECT count(*) FROM legacy_records")
        val warnings=meta.optJSONArray("financialWarnings")?.length() ?: 0
        val blockers=org.json.JSONArray()
        if(status!="POSTED_AND_RECONCILED") blockers.put("الفواتير والمدفوعات القديمة لم تُرحّل محاسبياً")
        if(!cashVerified) blockers.put("لم تُطابق أرصدة الخزنات والمدوّر")
        if(warnings>0) blockers.put("يوجد $warnings تحذيراً في التاريخ المالي")
        if(legacyCount==0L) blockers.put("السجلات المالية المصدر غير متاحة")
        return JSONObject()
            .put("state",if(blockers.length()==0)"ACTIVE" else "PENDING_RECONCILIATION")
            .put("financialActive",blockers.length()==0)
            .put("financialPostingStatus",status)
            .put("cashContinuityVerified",cashVerified)
            .put("legacyRecordCount",legacyCount)
            .put("nativeInvoiceCount",invoiceCount)
            .put("nativeJournalEntryCount",journalCount)
            .put("sourceSha256",meta.optString("sourceSha256"))
            .put("sourceExportedAt",meta.optString("sourceExportedAt"))
            .put("blockingReasons",blockers)
    }

    fun requireReconciled(store: Store) {
        val result=inspect(store)
        require(result.optBoolean("financialActive",false)) {
            "الاستعادة المالية غير مكتملة: "+result.optJSONArray("blockingReasons").toString()
        }
    }
}
