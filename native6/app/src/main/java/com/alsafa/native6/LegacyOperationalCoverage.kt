package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only coverage audit. A restored archive is NOT an operational ledger.
 * Never use archive counts as proof of posted financial transactions.
 */
object LegacyOperationalCoverage {
    private val financialSections = listOf(
        "sales", "purchases", "payments", "supplierLedger",
        "cashTransactions", "partnerWithdrawals", "dailyClosings", "returns"
    )

    fun inspect(store: Store): JSONObject {
        val metadata = LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val expected = metadata.getJSONObject("sourceCollections")
        val sections = JSONArray()
        var archivedTotal = 0L
        var stagedTotal = 0L
        for (section in financialSections) {
            val archived = store.scalar(
                "SELECT count(*) FROM legacy_records WHERE category=?", section
            )
            val expectedCount = expected.optLong(section, 0L)
            require(archived == expectedCount) {
                "عدد سجلات $section لا يطابق النسخة المستعادة"
            }
            val stagingTable = if (section in listOf("sales", "purchases"))
                "legacy_invoice_staging" else "legacy_movement_staging"
            val exists = store.scalar(
                "SELECT count(*) FROM sqlite_master WHERE type='table' AND name=?",
                stagingTable
            ) == 1L
            val staged = if (!exists) 0L else if (stagingTable == "legacy_invoice_staging")
                store.scalar(
                    "SELECT count(*) FROM legacy_invoice_staging WHERE source_section=?",
                    section
                ) else store.scalar(
                    "SELECT count(*) FROM legacy_movement_staging WHERE category=?",
                    section
                )
            archivedTotal = Math.addExact(archivedTotal, archived)
            stagedTotal = Math.addExact(stagedTotal, staged)
            sections.put(JSONObject()
                .put("section", section)
                .put("expected", expectedCount)
                .put("archived", archived)
                .put("staged", staged)
                .put("archiveComplete", archived == expectedCount)
                .put("operationalVerified", false))
        }
        val gate = LegacyFinancialActivationGate.inspect(store)
        return JSONObject()
            .put("sourceSha256", metadata.optString("sourceSha256"))
            .put("financialSections", sections)
            .put("archivedFinancialRecords", archivedTotal)
            .put("stagedFinancialRecords", stagedTotal)
            .put("operationalFinancialActive", gate.optBoolean("financialActive", false))
            .put("activationBlockers", gate.optJSONArray("blockingReasons") ?: JSONArray())
            .put("nativeInvoices", store.scalar("SELECT count(*) FROM invoices"))
            .put("nativeReceipts", store.scalar("SELECT count(*) FROM receipts"))
            .put("nativeJournalLines", store.scalar("SELECT count(*) FROM journal"))
            .put("repairRecords", store.scalar("SELECT count(*) FROM repairs"))
            .put("readOnly", true)
    }
}
