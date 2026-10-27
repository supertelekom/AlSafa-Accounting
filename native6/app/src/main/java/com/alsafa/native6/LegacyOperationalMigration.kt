package com.alsafa.native6

import org.json.JSONObject

/**
 * One atomic, repeatable activation of historical sales receivables and purchase
 * payables. Archive remains immutable; historical cash, stock and profit are not
 * replayed. A failed activation rolls back BOTH subledgers.
 */
object LegacyOperationalMigration {
    fun activate(store:Store):JSONObject=store.transaction {
        val source=LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val customer=LegacyCustomerAccounts.activate(store)
        val supplier=LegacySupplierInvoices.activate(store)
        val customerCheck=LegacyCustomerAccounts.reconcile(store)
        val supplierCheck=LegacySupplierInvoices.reconcile(store)
        require(customerCheck.optBoolean("reconciled")) {
            "فشلت مطابقة ذمم العملاء؛ تم إلغاء التفعيل بالكامل"
        }
        require(supplierCheck.optBoolean("reconciled")) {
            "فشلت مطابقة ذمم الموردين؛ تم إلغاء التفعيل بالكامل"
        }
        JSONObject().put("sourceVerified",true)
            .put("customerActivation",customer)
            .put("supplierActivation",supplier)
            .put("customerReconciliation",customerCheck)
            .put("supplierReconciliation",supplierCheck)
            .put("historicalCashReplayed",false)
            .put("historicalStockReplayed",false)
            .put("historicalProfitReplayed",false)
            .put("supplierLedgerAutoMerged",false)
            .put("note","تم تفعيل الذمم التشغيلية دون تكرار عمليات الماضي؛ بقية السجلات تتطلب مطابقة تشغيلية مستقلة")
    }
}
