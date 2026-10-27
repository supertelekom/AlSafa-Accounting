package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Live balances of migrated invoice obligations. These are NOT historical sales
 * or purchases recognized again: only unpaid invoice snapshots plus new settlements.
 * SupplierLedger is deliberately excluded until its overlap is reconciled.
 */
object LegacyOperationalBalances {
    private fun hasTable(store:Store,name:String):Boolean =
        store.scalar("SELECT count(*) FROM sqlite_master WHERE type='table' AND name=?",name)>0L

    private fun sumByCurrency(store:Store,table:String,remaining:(String)->BigDecimal):JSONObject {
        val totals=linkedMapOf("USD" to BigDecimal.ZERO,"TRY" to BigDecimal.ZERO,"SYP" to BigDecimal.ZERO)
        for(row in store.rows("SELECT archive_id,currency FROM $table")) {
            val currency=row.getValue("currency")
            require(currency in totals) { "عملة دفتر الذمم غير معروفة" }
            totals[currency]=totals.getValue(currency).add(remaining(row.getValue("archive_id")))
        }
        return JSONObject().also { result ->
            for((currency,value) in totals) result.put(currency,value.toPlainString())
        }
    }

    fun snapshot(store:Store):JSONObject {
        val customers=hasTable(store,"legacy_customer_accounts")
        val suppliers=hasTable(store,"legacy_purchase_payables")
        val customerCount=if(customers)store.scalar("SELECT count(*) FROM legacy_customer_accounts") else 0L
        val supplierCount=if(suppliers)store.scalar("SELECT count(*) FROM legacy_purchase_payables") else 0L
        val customerBalances=if(customers) sumByCurrency(store,"legacy_customer_accounts") {
            LegacyCustomerAccounts.outstanding(store,it)
        } else JSONObject()
        val supplierBalances=if(suppliers) sumByCurrency(store,"legacy_purchase_payables") {
            LegacySupplierInvoices.remaining(store,it)
        } else JSONObject()
        val customerCheck=if(customers)LegacyCustomerAccounts.reconcile(store) else JSONObject().put("activated",false)
        val supplierCheck=if(suppliers)LegacySupplierInvoices.reconcile(store) else JSONObject().put("activated",false)
        val cash=JSONObject()
        for(currency in Currency.values()) {
            val byVault=JSONObject()
            for(vault in Store.vaults) {
                byVault.put(vault,BigDecimal.valueOf(store.balance(vault,currency),2).toPlainString())
            }
            cash.put(currency.name,byVault)
        }
        val issues=JSONArray()
        if(customers && !customerCheck.optBoolean("reconciled"))
            issues.put("ديون العملاء لا تطابق قيودها")
        if(suppliers && !supplierCheck.optBoolean("reconciled"))
            issues.put("ديون فواتير الموردين لا تطابق قيودها")
        return JSONObject()
            .put("customerInvoices",customerCount)
            .put("supplierPurchaseInvoices",supplierCount)
            .put("customerOutstandingExact",customerBalances)
            .put("purchasePayablesExact",supplierBalances)
            .put("cashByVault",cash)
            .put("customerReconciliation",customerCheck)
            .put("supplierReconciliation",supplierCheck)
            .put("issues",issues)
            .put("supplierLedgerIncluded",false)
            .put("historicalCashReplayed",false)
            .put("historicalStockReplayed",false)
            .put("note","الذمم التشغيلية من فواتير الأرشيف فقط؛ حساب المورد الافتتاحي المستقل يحتاج مطابقة قبل إضافته")
    }
}
