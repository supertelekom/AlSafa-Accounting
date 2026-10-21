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
            val overpaid=(opening.keys+paid.keys).count { key -> (paid[key] ?: BigDecimal.ZERO) > (opening[key] ?: BigDecimal.ZERO) }
            if(overpaid>0) lines += "تحذير: $overpaid حساب مورد تجاوزت دفعات سجل الدين الافتتاحي فيه قيمة الدين؛ يلزم تدقيق قبل الترحيل."

        }
        // A recovered supplier payment is often also present as a cash transaction.
        // Report the shared identity; never treat the two records as two payments.
        data.optJSONArray("supplierLedger")?.let { ledger ->
            val cashIds=HashSet<String>()
            val cashById=HashMap<String,JSONObject>()
            data.optJSONArray("cashTransactions")?.let { transactions ->
                for(i in 0 until transactions.length()) {
                    val id=transactions.optJSONObject(i)?.optString("id").orEmpty()
                    if(id.isNotBlank()) { cashIds.add(id); cashById[id]=transactions.optJSONObject(i)!! }
                }
            }
            var linked=0
            var missing=0
            var duplicate=0
            var inconsistent=0
            val seen=HashSet<String>()
            for(i in 0 until ledger.length()) {
                val row=ledger.optJSONObject(i) ?: continue
                if(row.optString("kind")!="payment") continue
                val cashId=row.optString("recoveredFromCashTransactionId")
                if(cashId.isBlank()) continue
                if(!seen.add(cashId)) duplicate++
                if(cashId in cashIds) {
                    linked++
                    val original=cashById[cashId]!!
                    val amount=row.optString("amount").toBigDecimalOrNull()
                    val cashAmount=original.optString("amount").toBigDecimalOrNull()
                    if(amount==null || cashAmount==null || amount.abs()!=cashAmount.abs() || row.optString("currency")!=original.optString("currency")) inconsistent++
                } else missing++
            }
            lines += "مدفوعات المورد المستعادة من حركات الخزنة: مرتبطة $linked، مرجع نقدي مفقود $missing، مرجع مستعاد مكرر $duplicate"
            if(inconsistent>0) lines += "تحذير: $inconsistent دفعة مورد مستعادة لا تطابق مبلغ أو عملة حركة الخزنة."
            if(linked>0 || missing>0 || duplicate>0)
                lines += "هذه المدفوعات ليست حركات دفع جديدة؛ يمنع إعادة خصمها من الخزنة أثناء الترحيل."
        }
        // Never combine different currencies into one closing balance.
        // This is a descriptive audit, not an accounting posting.
        cash?.let { movements ->
            val daily=sortedMapOf<String,Pair<BigDecimal,Int>>()
            var missingFields=0
            for(i in 0 until movements.length()) {
                val row=movements.optJSONObject(i)
                if(row==null) {missingFields++;continue}
                val date=row.optString("date")
                val currency=row.optString("currency")
                val amount=row.optString("amount").toBigDecimalOrNull()
                if(!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) ||
                    currency !in listOf("USD","TRY","SYP") || amount==null) {
                    missingFields++;continue
                }
                val key="${date.substring(0,7)} | $currency"
                val prior=daily[key] ?: (BigDecimal.ZERO to 0)
                daily[key]=prior.first.add(amount) to (prior.second+1)
            }
            lines += "إجماليات مبالغ الحركات الخام شهريًا (ليست أرصدة ولا أرباح):"
            daily.forEach { (key,value) ->
                lines += "$key: ${value.first.toPlainString()} (${value.second} حركة)"
            }
            if(missingFields>0)
                lines += "حركات نقدية دون تاريخ أو عملة أو مبلغ صالح للتجميع: $missingFields"
        }
        // Unpaid purchase invoice portions are distinct from opening supplier debt.
        data.optJSONArray("purchases")?.let { purchases ->
            val remaining=sortedMapOf<String,BigDecimal>()
            var invalid=0
            for(i in 0 until purchases.length()) {
                val row=purchases.optJSONObject(i)
                if(row==null) {invalid++;continue}
                val supplier=row.optString("supplierId")
                val currency=row.optString("currency")
                val total=row.optString("total").toBigDecimalOrNull()
                val paid=row.optString("paid").toBigDecimalOrNull()
                if(supplier.isBlank() || currency !in listOf("USD","TRY","SYP") ||
                    total==null || paid==null || total.signum()<0 ||
                    paid.signum()<0 || paid>total) {invalid++;continue}
                val key="$supplier | $currency"
                remaining[key]=(remaining[key] ?: BigDecimal.ZERO)
                    .add(total.subtract(paid))
            }
            lines += "متبقي فواتير الشراء وفق المدفوع المسجل في الفاتورة فقط:"
            remaining.forEach { (key,amount) ->
                lines += "$key: ${amount.toPlainString()}"
            }
            if(invalid>0) lines += "فواتير شراء تحتاج مراجعة: $invalid"
            lines += "تنبيه: لا يُجمع هذا المبلغ مع سجل المورد إلا بعد فحص المدفوعات المرتبطة لمنع التكرار."
        }
        data.optJSONArray("sales")?.let { sales ->
            val unpaid=sortedMapOf<String,BigDecimal>()
            var invalid=0
            for(i in 0 until sales.length()) {
                val row=sales.optJSONObject(i)
                val currency=row?.optString("currency").orEmpty()
                val total=row?.optString("total")?.toBigDecimalOrNull()
                val paid=row?.optString("paid")?.toBigDecimalOrNull()
                if(currency !in listOf("USD","TRY","SYP") || total==null ||
                    paid==null || paid< BigDecimal.ZERO || total<paid) {
                    invalid++
                    continue
                }
                unpaid[currency]=(unpaid[currency] ?: BigDecimal.ZERO).add(total.subtract(paid))
            }
            lines += "متبقي المبيعات حسب حقول الفواتير فقط، قبل مطابقة الدفعات اللاحقة:"
            unpaid.forEach { (currency,amount) ->
                lines += "$currency: ${amount.toPlainString()}"
            }
            if(invalid>0) lines += "فواتير بيع غير قابلة للتجميع: $invalid"
        }
        data.optJSONArray("payments")?.let { payments ->
            val counts=sortedMapOf<String,Int>()
            for(i in 0 until payments.length()) {
                val row=payments.optJSONObject(i) ?: continue
                val type=row.optString("referenceType","غير محدد")
                counts[type]=(counts[type] ?: 0)+1
            }
            lines += "أنواع المدفوعات اللاحقة التي تحتاج مطابقة:"
            counts.forEach { (type,count) -> lines += "$type: $count" }
        }
        // Count later payments by their original reference and payment currency.
        // Never subtract a payment twice or convert across currencies implicitly.
        data.optJSONArray("payments")?.let { payments ->
            val totals=sortedMapOf<String,BigDecimal>()
            val seen=HashSet<String>()
            var invalid=0
            var duplicate=0
            for(i in 0 until payments.length()) {
                val row=payments.optJSONObject(i)
                if(row==null) {invalid++;continue}
                val id=row.optString("id")
                if(id.isNotBlank() && !seen.add(id)) {duplicate++;continue}
                val type=row.optString("referenceType")
                val ref=row.optString("referenceId")
                val currency=row.optString("paymentCurrency")
                    .ifBlank { row.optString("currency") }
                val amount=if(row.optString("paymentCurrency").isNotBlank())
                    row.optString("paymentAmount").toBigDecimalOrNull()
                    else row.optString("amount").toBigDecimalOrNull()
                if(type.isBlank() || ref.isBlank() ||
                    currency !in listOf("USD","TRY","SYP") ||
                    amount==null || amount.signum()<0) {invalid++;continue}
                val key="$type | $ref | $currency"
                totals[key]=(totals[key] ?: BigDecimal.ZERO).add(amount)
            }
            lines += "مدفوعات لاحقة بحسب مرجع العملية وعملة التحصيل (دون خصم تلقائي):"
            totals.entries.take(50).forEach { (key,value) ->
                lines += "$key: ${value.toPlainString()}"
            }
            if(totals.size>50) lines += "توجد ${totals.size-50} مراجع إضافية غير معروضة."
            if(invalid>0) lines += "مدفوعات تحتاج مراجعة: $invalid"
            if(duplicate>0) lines += "معرفات مدفوعات مكررة: $duplicate"
            lines += "تحذير: المدفوعات بعملة مختلفة عن الفاتورة تحتاج سعر صرف تاريخيًا مثبتًا قبل خصمها من الذمم."
        }
        // Source invoices may already include later receipts in their paid field.
        // Compare but never subtract the payment table from that field again.
        val saleRows=data.optJSONArray("sales")
        val paymentRows=data.optJSONArray("payments")
        if(saleRows!=null && paymentRows!=null) {
            val invoices=HashMap<String,Pair<String,BigDecimal>>()
            for(i in 0 until saleRows.length()) {
                val row=saleRows.optJSONObject(i) ?: continue
                val id=row.optString("id")
                val paid=row.optString("paid").toBigDecimalOrNull() ?: continue
                if(id.isNotBlank()) invoices[id]=row.optString("currency") to paid
            }
            var sameCurrency=0
            var otherCurrency=0
            var unspecifiedCurrency=0
            var missingInvoice=0
            for(i in 0 until paymentRows.length()) {
                val row=paymentRows.optJSONObject(i) ?: continue
                if(row.optString("referenceType")!="sale") continue
                val invoice=invoices[row.optString("referenceId")]
                if(invoice==null) {missingInvoice++;continue}
                val receiptCurrency=row.optString("paymentCurrency")
                    .ifBlank { row.optString("currency") }
                if(receiptCurrency.isBlank()) unspecifiedCurrency++
                else if(receiptCurrency==invoice.first) sameCurrency++ else otherCurrency++
            }
            lines += "مطابقة مدفوعات المبيع: نفس عملة الفاتورة $sameCurrency، عملة مختلفة $otherCurrency، فاتورة غير معروفة $missingInvoice، عملة تحصيل غير محددة $unspecifiedCurrency"
            lines += "المدفوع المحفوظ داخل الفاتورة قد يتضمن الدفعات اللاحقة؛ لا يُخصم سجل المدفوعات مرة ثانية."
        }
        data.optJSONArray("partnerWithdrawals")?.let { withdrawals ->
            val byMonth=sortedMapOf<String,BigDecimal>()
            var invalid=0
            for(i in 0 until withdrawals.length()) {
                val row=withdrawals.optJSONObject(i)
                val date=row?.optString("date").orEmpty()
                val partner=row?.optString("partnerId").orEmpty()
                val currency=row?.optString("currency").orEmpty()
                val amount=row?.optString("amount")?.toBigDecimalOrNull()
                if(date.length<7 || partner.isBlank() ||
                    currency !in listOf("USD","TRY","SYP") ||
                    amount==null || amount.signum()<0) {invalid++;continue}
                val key="${date.take(7)} | $partner | $currency"
                byMonth[key]=(byMonth[key] ?: BigDecimal.ZERO).add(amount)
            }
            lines += "سحوبات الشركاء بحسب شهر السحب الحقيقي (دون ترحيلها لشهر ربح سابق):"
            byMonth.forEach { (key,value) -> lines += "$key: ${value.toPlainString()}" }
            if(invalid>0) lines += "سحوبات تحتاج مراجعة: $invalid"
        }
        // Reconcile historical receipts without changing invoice balances.
        data.optJSONArray("payments")?.let { payments ->
            var converted=0
            var invalidFx=0
            val convertedPairs=sortedMapOf<String,Int>()
            for(i in 0 until payments.length()) {
                val row=payments.optJSONObject(i) ?: continue
                val source=row.optString("currency")
                val received=row.optString("paymentCurrency")
                if(source.isBlank() || received.isBlank() || source==received) continue
                converted++
                val pair="$source → $received"
                convertedPairs[pair]=(convertedPairs[pair] ?: 0)+1
                if(row.optString("paymentAmount").toBigDecimalOrNull()==null ||
                    !row.has("rateSnapshot") || row.isNull("rateSnapshot")) invalidFx++
            }
            lines += "دفعات بعملة مختلفة عن الفاتورة: $converted"
            convertedPairs.forEach { (pair,count) -> lines += "$pair: $count" }
            if(invalidFx>0) lines += "دفعات تحتاج تدقيق مبلغ التحصيل أو سعر الصرف: $invalidFx"
        }
        lines += "مهم: حركات الصناديق قد تعكس المبيعات والصيانة والسحوبات نفسها. لا تُسجل ثانيةً كدخل أو مصروف."
        return lines.joinToString("\n")
    }
}
