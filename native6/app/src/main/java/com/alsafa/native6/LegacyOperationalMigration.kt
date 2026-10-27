package com.alsafa.native6

import org.json.JSONObject

/**
 * One atomic, repeatable activation of historical sales receivables and purchase
 * payables. Archive remains immutable; historical cash, stock and profit are not
 * replayed. A failed activation rolls back BOTH subledgers.
 */
object LegacyOperationalMigration {
    fun activate(store:Store):JSONObject=store.transaction {
        LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val salesCount=store.legacyRows("sales").size
        val purchasesCount=store.legacyRows("purchases").size
        val customer=LegacyCustomerAccounts.activate(store)
        val supplier=LegacySupplierInvoices.activate(store)
        require(store.scalar("SELECT count(*) FROM legacy_customer_accounts")==salesCount.toLong())
        require(store.scalar("SELECT count(*) FROM legacy_purchase_payables")==purchasesCount.toLong())
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
