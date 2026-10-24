package com.alsafa.native6

import org.json.JSONObject
import java.math.BigDecimal

/**
 * Read-only supplier liability worksheet. The original ledger is not posted.
 * Purchases use their cumulative paid snapshot; ledger payments are separate
 * settlements of opening debt and must not be blindly applied twice.
 */
object LegacySupplierDebtAudit {
    private val zero=BigDecimal.ZERO
    private fun money(row:JSONObject,field:String):BigDecimal =
        row.optString(field,"0").toBigDecimalOrNull()
            ?: throw IllegalArgumentException("Invalid $field")

    fun inspect(payload:String):JSONObject {
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup" &&
            root.optInt("formatVersion")>=2) { "صيغة النسخة غير مدعومة" }
        val data=root.getJSONObject("data")
        val supplierNames=HashMap<String,String>()
        val suppliers=data.optJSONArray("suppliers")
        if(suppliers!=null) for(i in 0 until suppliers.length()) {
            val row=suppliers.optJSONObject(i) ?: continue
            supplierNames[row.optString("id")]=row.optString("name")
        }
        val amounts=linkedMapOf<String,Array<BigDecimal>>()
        fun bucket(id:String,currency:String):Array<BigDecimal> =
            amounts.getOrPut("$id|$currency") { Array(4) { zero } }
        val purchases=data.optJSONArray("purchases")
        if(purchases!=null) for(i in 0 until purchases.length()) {
            val row=purchases.optJSONObject(i) ?: continue
            val id=row.optString("supplierId")
            val currency=row.optString("currency")
            require(currency in setOf("USD","TRY","SYP")) { "purchases[$i]: currency" }
            val total=money(row,"total")
            val paid=money(row,"paid")
            require(total>=zero && paid>=zero && paid<=total) { "purchases[$i]: paid" }
            bucket(id,currency)[0]+=total-paid
        }
        val ledger=data.optJSONArray("supplierLedger")
        val unknown=org.json.JSONArray()
        if(ledger!=null) for(i in 0 until ledger.length()) {
            val row=ledger.optJSONObject(i) ?: continue
            val id=row.optString("supplierId")
            val currency=row.optString("currency")
            val kind=row.optString("kind").trim()
            val amount=money(row,"amount")
            require(currency in setOf("USD","TRY","SYP") && amount>=zero) {
                "supplierLedger[$i]: amount or currency"
            }
            val entry=bucket(id,currency)
            when(kind) {
                "opening-debt" -> entry[1]+=amount
                "payment" -> entry[2]+=amount
                else -> {
                    entry[3]+=amount
                    unknown.put("$i:$kind")
                }
            }
        }
        val rows=org.json.JSONArray()
        for((key,values) in amounts) {
            val id=key.substringBeforeLast("|")
            val currency=key.substringAfterLast("|")
            val provisional=values[0]+values[1]-values[2]
            rows.put(JSONObject()
                .put("supplierId",id).put("supplierName",supplierNames[id] ?: "")
                .put("currency",currency)
                .put("unpaidPurchases",values[0].toPlainString())
                .put("openingDebt",values[1].toPlainString())
                .put("ledgerPayments",values[2].toPlainString())
                .put("unclassifiedLedgerAmount",values[3].toPlainString())
                .put("provisionalBalance",provisional.toPlainString())
                .put("negativeBalanceFlag",provisional<zero)
                .put("unknownSupplierFlag",id.isNotBlank() && id !in supplierNames)
                .put("requiresReconciliation",true))
        }
        return JSONObject().put("suppliers",rows)
            .put("unclassifiedKinds",unknown)
            .put("posting","NONE")
            .put("warning","الأرصدة تقديرية: يجب التحقق من أن مدفوعات دفتر المورد لم تدخل أيضاً ضمن المدفوع في المشتريات")
    }
}
