package com.alsafa.native6

import org.json.JSONObject
import java.math.BigDecimal

/**
 * Referential audit of the original backup. Never posts journal entries or
 * infers balances from duplicated payment and cash representations.
 */
object LegacyReconciliation {
    fun inspect(payload:String):String {
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup"){"ملف غير مدعوم"}
        val data=root.getJSONObject("data")
        val sources=mapOf(
            "sale" to "sales", "repair" to "repairs",
            "purchase" to "purchases", "expense" to "expenses",
            "partnerWithdrawal" to "partnerWithdrawals",
            "return" to "returns"
        )
        val ids=sources.mapValues { (_,section) ->
            val list=data.optJSONArray(section)
            val found=HashSet<String>()
            if(list!=null) for(i in 0 until list.length()) {
                val id=list.optJSONObject(i)?.optString("id").orEmpty()
                if(id.isNotBlank()) found.add(id)
            }
            found
        }
        val lines=mutableListOf("مطابقة مراجع العمليات القديمة — قراءة فقط")
        for(section in listOf("cashTransactions","payments")) {
            val list=data.optJSONArray(section)
            if(list==null) { lines += "$section: القسم غير موجود";continue }
            var linked=0
            var missing=0
            var unknown=0
            var unlinked=0
            val missingSamples=mutableListOf<String>()
            val counts=sortedMapOf<String,Int>()
            for(i in 0 until list.length()) {
                val row=list.optJSONObject(i)
                if(row==null) {unknown++;continue}
                val type=row.optString("referenceType")
                val id=row.optString("referenceId")
                if(id.isBlank()) {unlinked++;continue}
                counts[type]=(counts[type] ?: 0)+1
                val known=ids[type]
                if(known==null) unknown++
                else if(id in known) linked++
                else {
                    missing++
                    if(missingSamples.size<12) missingSamples.add("$type / $id")
                }
            }
            lines += "$section — مراجع صحيحة: $linked، مفقودة: $missing، أنواع تحتاج ربطًا: $unknown، بلا مرجع: $unlinked"
            lines += "$section — أنواع المراجع: " +
                counts.entries.joinToString("، ") { "${it.key}: ${it.value}" }
            if(missingSamples.isNotEmpty())
                lines += "$section — أمثلة المراجع المفقودة: " + missingSamples.joinToString("، ")
        }
        val cash=data.optJSONArray("cashTransactions")
        if(cash!=null) {
            val groups=sortedMapOf<String,Int>()
            for(i in 0 until cash.length()) {
                val row=cash.optJSONObject(i) ?: continue
                val type=row.optString("type","غير محدد")
                groups[type]=(groups[type] ?: 0)+1
            }
            lines += "تصنيف الحركات النقدية الأصلية:"
            groups.forEach { (type,count) -> lines += "$type: $count" }
        }
        // Reconcile opening supplier debt against its own ledger payments only.
        // Purchase invoices are separate liabilities and must be considered later.
        data.optJSONArray("supplierLedger")?.let { ledger ->
            val opening=sortedMapOf<String,BigDecimal>()
            val paid=sortedMapOf<String,BigDecimal>()
            var unsupported=0
            for(i in 0 until ledger.length()) {
                val row=ledger.optJSONObject(i)
                if(row==null) {unsupported++;continue}
                val supplier=row.optString("supplierId")
                val currency=row.optString("currency")
                val amount=row.optString("amount").toBigDecimalOrNull()
                if(supplier.isBlank() || currency !in listOf("USD","TRY","SYP") ||
                    amount==null || amount.signum()<0) {unsupported++;continue}
                val key="$supplier | $currency"
                when(row.optString("kind")) {
                    "opening-debt" -> opening[key]=(opening[key] ?: BigDecimal.ZERO).add(amount)
                    "payment" -> paid[key]=(paid[key] ?: BigDecimal.ZERO).add(amount)
                    else -> unsupported++
                }
            }
            lines += "تسوية ديون الموردين الافتتاحية فقط — لا تشمل فواتير الشراء:"
            (opening.keys+paid.keys).sorted().forEach { key ->
                val due=opening[key] ?: BigDecimal.ZERO
                val settled=paid[key] ?: BigDecimal.ZERO
                lines += "$key: افتتاحي ${due.toPlainString()}، مدفوع ${settled.toPlainString()}، الفرق ${due.subtract(settled).toPlainString()}"
            }
            if(unsupported>0) lines += "قيود مورد غير مصنفة: $unsupported"
        }
        lines += "مهم: حركات الصناديق قد تعكس المبيعات والصيانة والسحوبات نفسها. لا تُسجل ثانيةً كدخل أو مصروف."
        return lines.joinToString("\n")
    }
}
