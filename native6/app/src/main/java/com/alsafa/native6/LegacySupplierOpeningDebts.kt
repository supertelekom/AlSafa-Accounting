package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Independent supplier opening debt evidence from supplierLedger.
 * Deliberately NOT added to invoice payables: historical supplier payments
 * may already have settled invoice debt. No journal posting in this module.
 */
object LegacySupplierOpeningDebts {
    fun snapshot(store:Store):JSONObject {
        val meta=LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val source=store.legacyRows("supplierLedger")
        require(source.size.toLong()==meta.getJSONObject("sourceCollections")
            .optLong("supplierLedger",-1)) { "عدد حركات الموردين القديمة غير مطابق" }
        val supplierNames=store.rows("SELECT id,name FROM suppliers")
            .associate { it.getValue("id") to it.getValue("name") }
        val positions=linkedMapOf<Pair<String,String>,BigDecimal>()
        val entries=JSONArray()
        val kinds=linkedMapOf<String,Int>()
        val seen=hashSetOf<String>()
        for(record in source) {
            val obj=JSONObject(record.getValue("payload"))
            val id=obj.optString("id")
            require(id.isNotBlank() && seen.add(id)) { "معرف حركة مورد غير صالح أو مكرر" }
            val supplier=obj.optString("supplierId")
            require(supplier in supplierNames) { "مورد تاريخي غير موجود" }
            val currency=obj.optString("currency")
            require(currency in setOf("USD","TRY","SYP")) { "عملة غير مدعومة" }
            val amount=obj.optString("amount","0").toBigDecimalOrNull()
                ?: error("مبلغ حركة مورد غير صالح")
            require(amount.signum()>=0) { "مبلغ حركة مورد سالب" }
            val kind=obj.optString("kind","unknown")
            kinds[kind]=(kinds[kind]?:0)+1
            if(kind!="opening-debt" && kind!="payment") continue
            val key=supplier to currency
            val change=if(kind=="opening-debt") amount else amount.negate()
            positions[key]=(positions[key]?:BigDecimal.ZERO).add(change)
            entries.put(JSONObject().put("sourceId",id).put("supplierId",supplier)
                .put("currency",currency).put("kind",kind)
                .put("amountExact",amount.toPlainString()))
        }
        val balances=JSONArray()
        for((key,balance) in positions) {
            balances.put(JSONObject().put("supplierId",key.first)
                .put("supplierName",supplierNames.getValue(key.first))
                .put("currency",key.second)
                .put("netOpeningDebtExact",balance.toPlainString())
                .put("possibleAdvance",balance.signum()<0))
        }
        val typeCounts=JSONObject()
        for((kind,count) in kinds) typeCounts.put(kind,count)
        return JSONObject().put("sourceRows",source.size)
            .put("openingAndPaymentEntries",entries)
            .put("supplierBalances",balances)
            .put("ledgerKindCounts",typeCounts)
            .put("readOnly",true).put("posted",false)
            .put("includedInPurchasePayables",false)
            .put("warning","هذه أرصدة دفتر المورد القديم مستقلة عن فواتير الشراء؛ لا يجوز جمعها قبل تحديد تداخل الدفعات")
    }
}
