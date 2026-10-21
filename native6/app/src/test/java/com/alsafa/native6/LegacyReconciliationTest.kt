package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyReconciliationTest {
    @Test fun differentiatesLinkedMissingAndUnlinkedCashTransactions() {
        val data=JSONObject()
            .put("sales",JSONArray().put(JSONObject().put("id","sale-1")))
            .put("repairs",JSONArray().put(JSONObject().put("id","repair-1")))
            .put("cashTransactions",JSONArray()
                .put(JSONObject().put("referenceType","sale").put("referenceId","sale-1").put("type","income"))
                .put(JSONObject().put("referenceType","sale").put("referenceId","sale-404").put("type","income"))
                .put(JSONObject().put("referenceType","currencyExchange").put("referenceId","exchange-1").put("type","currency-exchange-in"))
                .put(JSONObject().put("referenceType","").put("referenceId","").put("type","transfer-in")))
            .put("payments",JSONArray()
                .put(JSONObject().put("referenceType","repair").put("referenceId","repair-1")))
        val payload=JSONObject().put("format","alsafa-accounting-backup")
            .put("data",data).toString()
        val report=LegacyReconciliation.inspect(payload)
        assertTrue(report.contains("cashTransactions — مراجع صحيحة: 1، مفقودة: 1، أنواع تحتاج ربطًا: 1، بلا مرجع: 1"))
        assertTrue(report.contains("payments — مراجع صحيحة: 1، مفقودة: 0"))
        assertTrue(report.contains("sale / sale-404"))
    }
    @Test fun openingSupplierDebtIsSeparateFromPurchaseLiabilities() {
        val ledger=JSONArray()
            .put(JSONObject().put("supplierId","supplier-1").put("currency","USD")
                .put("kind","opening-debt").put("amount",1500))
            .put(JSONObject().put("supplierId","supplier-1").put("currency","USD")
                .put("kind","payment").put("amount",500))
        val data=JSONObject().put("supplierLedger",ledger)
            .put("purchases",JSONArray().put(JSONObject().put("id","purchase-1")
                .put("supplierId","supplier-1").put("total",250)))
        val payload=JSONObject().put("format","alsafa-accounting-backup")
            .put("data",data).toString()
        val report=LegacyReconciliation.inspect(payload)
        assertTrue(report.contains("supplier-1 | USD: افتتاحي 1500، مدفوع 500، الفرق 1000"))
        assertTrue(report.contains("لا تشمل فواتير الشراء"))
    }

    @Test fun rawCashMonthlyAuditNeverCombinesCurrencies() {
        val movements=JSONArray()
            .put(JSONObject().put("date","2026-09-30").put("currency","USD")
                .put("amount",10))
            .put(JSONObject().put("date","2026-10-01").put("currency","USD")
                .put("amount",20))
            .put(JSONObject().put("date","2026-10-02").put("currency","TRY")
                .put("amount",500))
        val payload=JSONObject().put("format","alsafa-accounting-backup")
            .put("data",JSONObject().put("cashTransactions",movements)).toString()
        val report=LegacyReconciliation.inspect(payload)
        assertTrue(report.contains("2026-09 | USD: 10 (1 حركة)"))
        assertTrue(report.contains("2026-10 | USD: 20 (1 حركة)"))
        assertTrue(report.contains("2026-10 | TRY: 500 (1 حركة)"))
    }

    @Test fun unpaidSalesAreReportedByCurrencyWithoutAssumingPaymentAllocation() {
        val data=JSONObject().put("sales",JSONArray()
            .put(JSONObject().put("currency","USD").put("total",100).put("paid",30))
            .put(JSONObject().put("currency","TRY").put("total",200).put("paid",50)))
        val report=LegacyReconciliation.inspect(JSONObject()
            .put("format","alsafa-accounting-backup").put("data",data).toString())
        assertTrue(report.contains("USD: 70"))
        assertTrue(report.contains("TRY: 150"))
    }

    @Test fun paymentsKeepCollectionCurrency() {
        val p=JSONObject().put("id","p1").put("referenceType","sale")
            .put("referenceId","s1").put("currency","USD")
            .put("amount",10).put("paymentCurrency","TRY").put("paymentAmount",420)
        val d=JSONObject().put("payments",JSONArray().put(p))
        val root=JSONObject().put("format","alsafa-accounting-backup").put("data",d)
        assertTrue(LegacyReconciliation.inspect(root.toString())
            .contains("sale | s1 | TRY: 420"))
    }

    @Test fun laterPaymentAuditDoesNotDoubleDeductInvoicePaid() {
        val sales=JSONArray().put(JSONObject().put("id","s1")
            .put("currency","USD").put("total",100).put("paid",60))
        val payments=JSONArray().put(JSONObject().put("id","p1")
            .put("referenceType","sale").put("referenceId","s1")
            .put("paymentCurrency","USD").put("paymentAmount",20))
        val data=JSONObject().put("sales",sales).put("payments",payments)
        val report=LegacyReconciliation.inspect(JSONObject()
            .put("format","alsafa-accounting-backup").put("data",data).toString())
        assertTrue(report.contains("USD: 40"))
        assertTrue(report.contains("نفس عملة الفاتورة 1"))
    }

    @Test fun partnerWithdrawalsStayInTheirActualMonth() {
        val w=JSONArray().put(JSONObject().put("date","2026-09-30")
            .put("partnerId","father").put("currency","USD").put("amount",12))
            .put(JSONObject().put("date","2026-10-01")
                .put("partnerId","father").put("currency","USD").put("amount",8))
        val root=JSONObject().put("format","alsafa-accounting-backup")
            .put("data",JSONObject().put("partnerWithdrawals",w))
        val report=LegacyReconciliation.inspect(root.toString())
        assertTrue(report.contains("2026-09 | father | USD: 12"))
        assertTrue(report.contains("2026-10 | father | USD: 8"))
    }

    @Test fun recoveredSupplierPaymentIsNotTreatedAsNewCashOutflow() {
        val ledger=JSONArray()
            .put(JSONObject().put("supplierId","supplier-1").put("currency","USD")
                .put("kind","opening-debt").put("amount",520))
            .put(JSONObject().put("supplierId","supplier-1").put("currency","USD")
                .put("kind","payment").put("amount",520)
                .put("recoveredFromCashTransactionId","cash-1"))
        val data=JSONObject().put("supplierLedger",ledger)
            .put("cashTransactions",JSONArray().put(JSONObject()
                .put("id","cash-1").put("currency","USD").put("amount",520)))
        val report=LegacyReconciliation.inspect(JSONObject()
            .put("format","alsafa-accounting-backup").put("data",data).toString())
        assertTrue(report.contains("مرتبطة 1، مرجع نقدي مفقود 0، مرجع مستعاد مكرر 0"))
        assertTrue(report.contains("يمنع إعادة خصمها من الخزنة"))
        assertTrue(report.contains("الفرق 0"))
    }
    @Test fun missingAndDuplicateRecoveredSupplierCashReferencesAreFlagged() {
        val ledger=JSONArray()
            .put(JSONObject().put("kind","payment")
                .put("recoveredFromCashTransactionId","cash-missing"))
            .put(JSONObject().put("kind","payment")
                .put("recoveredFromCashTransactionId","cash-missing"))
        val data=JSONObject().put("supplierLedger",ledger)
            .put("cashTransactions",JSONArray())
        val report=LegacyReconciliation.inspect(JSONObject()
            .put("format","alsafa-accounting-backup").put("data",data).toString())
        assertTrue(report.contains("مرتبطة 0، مرجع نقدي مفقود 2، مرجع مستعاد مكرر 1"))
    }

    @Test fun supplierOpeningOverpaymentRequiresReviewBeforeMigration() {
        val ledger=JSONArray()
            .put(JSONObject().put("supplierId","s").put("currency","USD")
                .put("kind","opening-debt").put("amount",100))
            .put(JSONObject().put("supplierId","s").put("currency","USD")
                .put("kind","payment").put("amount",150))
        val data=JSONObject().put("supplierLedger",ledger)
        val report=LegacyReconciliation.inspect(JSONObject()
            .put("format","alsafa-accounting-backup").put("data",data).toString())
        assertTrue(report.contains("تجاوزت دفعات سجل الدين الافتتاحي"))
    }

    @Test fun recoveredSupplierCashAmountOrCurrencyMismatchIsFlagged() {
        val ledger=JSONArray().put(JSONObject()
            .put("supplierId","s").put("kind","payment").put("currency","USD")
            .put("amount",520).put("recoveredFromCashTransactionId","cash-1"))
        val cash=JSONArray().put(JSONObject()
            .put("id","cash-1").put("currency","TRY").put("amount",520))
        val data=JSONObject().put("supplierLedger",ledger).put("cashTransactions",cash)
        val report=LegacyReconciliation.inspect(JSONObject()
            .put("format","alsafa-accounting-backup").put("data",data).toString())
        assertTrue(report.contains("لا تطابق مبلغ أو عملة حركة الخزنة"))
    }

    @Test fun duplicateCashTransactionIdentifiersBlockBlindSupplierMatching() {
        val cash=JSONArray()
            .put(JSONObject().put("id","cash-1").put("currency","USD").put("amount",520))
            .put(JSONObject().put("id","cash-1").put("currency","USD").put("amount",520))
        val ledger=JSONArray().put(JSONObject().put("kind","payment")
            .put("currency","USD").put("amount",520)
            .put("recoveredFromCashTransactionId","cash-1"))
        val data=JSONObject().put("cashTransactions",cash).put("supplierLedger",ledger)
        val report=LegacyReconciliation.inspect(JSONObject()
            .put("format","alsafa-accounting-backup").put("data",data).toString())
        assertTrue(report.contains("معرف حركة خزنة مكرر"))
    }

    @Test fun purchaseDebtAuditFlagsDuplicateInvoiceAndMissingSupplier() {
        val suppliers=JSONArray().put(JSONObject().put("id","known"))
        val purchases=JSONArray()
            .put(JSONObject().put("id","purchase-1").put("supplierId","known")
                .put("currency","USD").put("total",100).put("paid",20))
            .put(JSONObject().put("id","purchase-1").put("supplierId","missing")
                .put("currency","USD").put("total",100).put("paid",120))
        val data=JSONObject().put("suppliers",suppliers).put("purchases",purchases)
        val report=LegacyReconciliation.inspect(JSONObject()
            .put("format","alsafa-accounting-backup").put("data",data).toString())
        assertTrue(report.contains("معرفات مكررة 1، مورد غير معروف 1، قيم مدفوعة غير صالحة 1"))
        assertTrue(report.contains("فواتير شراء تحتاج مراجعة قبل اعتماد"))
    }

    @Test fun purchasePaymentReferenceDoesNotDoubleSubtractInvoicePaid() {
        val purchases=JSONArray().put(JSONObject().put("id","p-1")
            .put("supplierId","s").put("currency","USD").put("total",100).put("paid",40))
        val payments=JSONArray().put(JSONObject().put("id","payment-1")
            .put("referenceType","purchase").put("referenceId","p-1")
            .put("currency","USD").put("amount",40))
        val data=JSONObject().put("purchases",purchases).put("payments",payments)
        val report=LegacyReconciliation.inspect(JSONObject()
            .put("format","alsafa-accounting-backup").put("data",data).toString())
        assertTrue(report.contains("s | USD: 60"))
        assertTrue(report.contains("ربط دفعات فواتير الشراء: مراجع صحيحة 1، فواتير مفقودة 0"))
        assertTrue(report.contains("يمنع خصمها مرة ثانية"))
    }

    @Test fun purchaseWithBlankIdIsNotSilentlyMergedWithOtherInvoices() {
        val purchases=JSONArray()
            .put(JSONObject().put("id","").put("supplierId","s")
                .put("currency","USD").put("total",20).put("paid",0))
            .put(JSONObject().put("id","").put("supplierId","s")
                .put("currency","USD").put("total",30).put("paid",0))
        val data=JSONObject().put("purchases",purchases)
        val report=LegacyReconciliation.inspect(JSONObject()
            .put("format","alsafa-accounting-backup").put("data",data).toString())
        assertTrue(report.contains("فواتير شراء بلا معرف 2"))
        assertTrue(report.contains("s | USD: 50"))
    }

    @Test fun emptySupplierDirectoryDoesNotValidatePurchaseSupplier() {
        val data=JSONObject().put("suppliers",JSONArray())
            .put("purchases",JSONArray().put(JSONObject()
                .put("id","purchase-1").put("supplierId","unknown")
                .put("currency","USD").put("total",10).put("paid",0)))
        val report=LegacyReconciliation.inspect(JSONObject()
            .put("format","alsafa-accounting-backup").put("data",data).toString())
        assertTrue(report.contains("مورد غير معروف 1"))
    }

    @Test fun paymentCannotResolveToDuplicatedPurchaseIdentifier() {
        val purchases=JSONArray()
            .put(JSONObject().put("id","p").put("currency","USD").put("total",50).put("paid",0))
            .put(JSONObject().put("id","p").put("currency","USD").put("total",70).put("paid",0))
        val payments=JSONArray().put(JSONObject().put("referenceType","purchase")
            .put("referenceId","p").put("currency","USD").put("amount",20))
        val data=JSONObject().put("purchases",purchases).put("payments",payments)
        val report=LegacyReconciliation.inspect(JSONObject()
            .put("format","alsafa-accounting-backup").put("data",data).toString())
        assertTrue(report.contains("1 دفعة مرتبطة بمعرف فاتورة شراء مكرر"))
        assertTrue(report.contains("مراجع صحيحة 0"))
    }

    @Test fun purchasePaymentIdentifiersRequireReviewWhenRepeatedOrBlank() {
        val payments=JSONArray()
            .put(JSONObject().put("id","same").put("referenceType","purchase").put("referenceId","p"))
            .put(JSONObject().put("id","same").put("referenceType","purchase").put("referenceId","p"))
            .put(JSONObject().put("id","").put("referenceType","purchase").put("referenceId","p"))
        val report=LegacyReconciliation.inspect(JSONObject()
            .put("format","alsafa-accounting-backup")
            .put("data",JSONObject().put("payments",payments)).toString())
        assertTrue(report.contains("دفعات شراء بمعرف مكرر 1، ودون معرف 1"))
    }

}
