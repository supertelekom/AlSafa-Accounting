package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Reconciliation matrix between the archived supplier ledger and purchase
 * invoice balances. Shared supplier/currency is a potential overlap, NOT
 * proof that either balance can be added or netted against the other.
 */
object LegacySupplierOverlap {
    fun inspect(store:Store):JSONObject {
        val ledger=LegacySupplierOpeningDebts.snapshot(store)
        LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val invoiceByParty=linkedMapOf<Pair<String,String>,BigDecimal>()
        val invoiceCounts=linkedMapOf<Pair<String,String>,Int>()
        val unidentified=JSONArray()
        for(record in store.legacyRows("purchases")) {
            val row=JSONObject(record.getValue("payload"))
            val key=row.optString("supplierId").trim()
            val currency=row.optString("currency")
            require(currency in setOf("USD","TRY","SYP"))
            val total=row.optString("total").toBigDecimalOrNull()
                ?: error("إجمالي فاتورة شراء غير صالح")
            val paid=row.optString("paid","0").toBigDecimalOrNull()
                ?: error("مدفوع فاتورة شراء غير صالح")
            require(total>=BigDecimal.ZERO && paid>=BigDecimal.ZERO && paid<=total)
            val due=total.subtract(paid)
            if(key.isBlank()) {
                if(due.signum()>0) unidentified.put(JSONObject()
                    .put("archiveId",record.getValue("legacy_id"))
                    .put("currency",currency).put("outstanding",due.toPlainString()))
                continue
            }
            val group=key to currency
            invoiceByParty[group]=(invoiceByParty[group]?:BigDecimal.ZERO).add(due)
            invoiceCounts[group]=(invoiceCounts[group]?:0)+1
        }
        val ledgerByParty=linkedMapOf<Pair<String,String>,BigDecimal>()
        val names=linkedMapOf<String,String>()
        val balances=ledger.getJSONArray("supplierBalances")
        for(i in 0 until balances.length()) {
            val item=balances.getJSONObject(i)
            val id=item.getString("supplierId")
            names[id]=item.optString("supplierName")
            ledgerByParty[id to item.getString("currency")]=
                item.getString("netOpeningDebtExact").toBigDecimal()
        }
        val groups=JSONArray()
        var overlapCount=0
        for(key in (invoiceByParty.keys+ledgerByParty.keys).distinct().sortedWith(
            compareBy<Pair<String,String>> { it.first }.thenBy { it.second })) {
            val invoiceDue=invoiceByParty[key]?:BigDecimal.ZERO
            val ledgerDue=ledgerByParty[key]?:BigDecimal.ZERO
            val possible=key in invoiceByParty && key in ledgerByParty
            if(possible) overlapCount++
            groups.put(JSONObject().put("supplierId",key.first)
                .put("supplierName",names[key.first]?:"")
                .put("currency",key.second)
                .put("purchaseInvoiceOutstanding",invoiceDue.toPlainString())
                .put("supplierLedgerNet",ledgerDue.toPlainString())
                .put("invoiceCount",invoiceCounts[key]?:0)
                .put("possibleOverlap",possible)
                .put("automaticallyCombined",false))
        }
        return JSONObject().put("groups",groups)
            .put("groupsWithPossibleOverlap",overlapCount)
            .put("unidentifiedPurchaseInvoices",unidentified)
            .put("supplierLedgerRows",ledger.getInt("sourceRows"))
            .put("readOnly",true).put("posting","NONE")
            .put("safeToAddBalances",false)
            .put("note","التطابق في المورد والعملة مؤشر تداخل فقط؛ لا يجوز جمع رصيد دفتر المورد مع فواتير الشراء تلقائياً")
    }
}
