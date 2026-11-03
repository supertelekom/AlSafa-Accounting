package com.alsafa.native8.data.migration

import org.json.JSONObject
import java.math.BigDecimal

/**
 * Read-only supplier reconciliation before any Room financial activation.
 * The archived purchase paid amount is cumulative. Supplier ledger entries
 * are evidence, not additional cash postings or automatic payables.
 */
class LegacySupplierReconciliation(private val source: LegacyReadRepository) {
    data class Balance(
        val supplierId: String,
        val currency: String,
        val invoiceDue: BigDecimal,
        val openingDebt: BigDecimal,
        val ledgerPayments: BigDecimal,
        val possibleOverlap: Boolean
    )
    data class Report(
        val supplierCount: Int,
        val purchaseCount: Int,
        val ledgerCount: Int,
        val balances: List<Balance>,
        val issues: List<String>,
        val safeForAutomaticPosting: Boolean = false
    )

    fun inspect(runId: Long): Report {
        fun all(section: String): List<LegacyReadRepository.LegacyRow> {
            val first = source.page(runId, section, 0, 500)
            val result = ArrayList(first.rows)
            var offset = result.size
            while (offset < first.total) {
                val next = source.page(runId, section, offset, 500)
                check(next.rows.isNotEmpty()) { "Incomplete staged section: $section" }
                result.addAll(next.rows)
                offset += next.rows.size
            }
            return result
        }
        val suppliers = all("suppliers")
        val purchases = all("purchases")
        val ledger = all("supplierLedger")
        val issues = mutableListOf<String>()
        val ids = suppliers.mapNotNull { it.id?.takeIf(String::isNotBlank) }.toSet()
        if (ids.size != suppliers.size) issues += "Duplicate or missing supplier IDs"
        val purchaseIds = purchases.mapNotNull { it.id?.takeIf(String::isNotBlank) }.toSet()
        if (purchaseIds.size != purchases.size) issues += "Duplicate or missing purchase IDs"
        data class Amounts(var due: BigDecimal = BigDecimal.ZERO,
                           var opening: BigDecimal = BigDecimal.ZERO,
                           var paid: BigDecimal = BigDecimal.ZERO,
                           var hasInvoice: Boolean = false,
                           var hasLedger: Boolean = false)
        val groups = linkedMapOf<Pair<String, String>, Amounts>()
        fun amount(obj: JSONObject, key: String): BigDecimal =
            obj.optString(key, "0").toBigDecimalOrNull()
                ?: throw IllegalArgumentException("Invalid $key")
        fun validCurrency(currency: String) = currency in setOf("USD", "TRY", "SYP")
        for (row in purchases) {
            val obj = row.fields
            val supplier = obj.optString("supplierId").trim()
            val currency = obj.optString("currency")
            val total = amount(obj, "total")
            val paid = amount(obj, "paid")
            if (supplier !in ids || !validCurrency(currency) ||
                total.signum() < 0 || paid.signum() < 0 || paid > total) {
                issues += "Invalid purchase: ${row.id ?: row.ordinal}"
                continue
            }
            val group = groups.getOrPut(supplier to currency) { Amounts() }
            group.due += total - paid
            group.hasInvoice = true
        }
        val ledgerIds = mutableSetOf<String>()
        for (row in ledger) {
            val obj = row.fields
            val supplier = obj.optString("supplierId").trim()
            val currency = obj.optString("currency")
            val kind = obj.optString("kind")
            val value = amount(obj, "amount")
            if (row.id.isNullOrBlank() || !ledgerIds.add(row.id) ||
                supplier !in ids || !validCurrency(currency) || value.signum() < 0) {
                issues += "Invalid supplier ledger entry: ${row.id ?: row.ordinal}"
                continue
            }
            val reference = obj.optString("referenceId").ifBlank { obj.optString("invoiceId") }
            if (reference.isNotBlank() && reference in purchaseIds) {
                val invoice = purchases.first { it.id == reference }.fields
                if (invoice.optString("supplierId") != supplier ||
                    invoice.optString("currency") != currency)
                    issues += "Conflicting purchase reference: ${row.id}"
            }
            val group = groups.getOrPut(supplier to currency) { Amounts() }
            group.hasLedger = true
            when (kind) {
                "opening-debt" -> group.opening += value
                "payment" -> group.paid += value
                else -> issues += "Unclassified supplier ledger kind: $kind (${row.id})"
            }
        }
        return Report(suppliers.size, purchases.size, ledger.size,
            groups.map { (key, value) ->
                Balance(key.first, key.second, value.due, value.opening,
                    value.paid, value.hasInvoice && value.hasLedger)
            }, issues)
    }
}
