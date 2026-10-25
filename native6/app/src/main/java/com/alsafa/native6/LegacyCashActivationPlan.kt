package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Read-only financial activation plan from the staged legacy cash audit.
 * Never posts a cash opening: historical signed movements alone are not
 * evidence of physically counted money or a reconciled daily closing.
 */
object LegacyCashActivationPlan {
    fun inspect(store: Store): JSONObject {
        val metadata = LegacyArchivedDebtReport.verifyArchiveCounts(store)
        val snapshot = metadata.optJSONObject("vaultSnapshot")
            ?: throw IllegalStateException("فحص الخزنات غير محفوظ ضمن بيانات الاستيراد")
        val source = snapshot.optJSONObject("balances")
            ?: throw IllegalStateException("أرصدة الخزنات القديمة غير موجودة")
        val blockers = JSONArray()
        fun block(condition: Boolean, reason: String) {
            if (condition) blockers.put(reason)
        }
        block(snapshot.optJSONArray("invalidMovementIndexes")?.length() != 0,
            "حركات نقدية غير صالحة")
        block(snapshot.optJSONArray("duplicateMovementIndexes")?.length() != 0,
            "معرفات حركات نقدية مكررة")
        block(snapshot.optJSONArray("negativeFinalBalances")?.length() != 0,
            "أرصدة خزنة سالبة")
        block(snapshot.optJSONArray("fractionalMinorUnitRows")?.length() != 0,
            "كسور نقدية غير مدعومة")
        block(metadata.optBoolean("cashContinuityVerified") != true,
            "مطابقة الإقفالات والأرصدة النقدية لم تعتمد")
        block(metadata.optString("financialPostingStatus") != "NOT_POSTED",
            "الحالة المالية للأرشيف غير متوقعة")
        val cashAudit = metadata.optJSONObject("paymentCashAudit")
        block(cashAudit == null, "فحص مطابقة الدفعات بالحركات النقدية غير موجود")
        if (cashAudit != null) {
            block((cashAudit.optJSONArray("unmatched")?.length() ?: -1) != 0,
                "دفعات لا تقابلها حركة نقدية مؤكدة")
            block((cashAudit.optJSONArray("ambiguous")?.length() ?: -1) != 0,
                "دفعات تقابلها حركات نقدية ملتبسة")
        }
        val supplierAudit = metadata.optJSONObject("supplierCashAudit")
        block(supplierAudit == null, "فحص مطابقة سداد الموردين بالخزنة غير موجود")
        if (supplierAudit != null) {
            block((supplierAudit.optJSONArray("unmatchedLedgerIndexes")?.length() ?: -1) != 0,
                "دفعات موردين غير مطابقة لحركات الخزنة")
            block((supplierAudit.optJSONArray("ambiguousCandidates")?.length() ?: -1) != 0,
                "دفعات موردين ذات مطابقة ملتبسة")
        }
        val closingAudit = metadata.optJSONObject("closingAudit")
        block(closingAudit == null, "فحص استمرارية الإقفال اليومي غير موجود")
        if (closingAudit != null) {
            block((closingAudit.optJSONArray("arithmeticMismatches")?.length() ?: -1) != 0,
                "فروقات حسابية في الإقفالات القديمة")
            block((closingAudit.optJSONArray("invalidDateRows")?.length() ?: -1) != 0,
                "تواريخ إقفال غير صالحة")
            block((closingAudit.optJSONArray("duplicateClosingDates")?.length() ?: -1) != 0,
                "إقفالات متكررة لنفس اليوم")
        }
        val paymentLinks = metadata.optJSONObject("paymentLinkAudit")
        block(paymentLinks == null, "فحص ارتباط الدفعات بالفواتير غير موجود")
        if (paymentLinks != null) {
            block((paymentLinks.optJSONArray("missingReferenceRows")?.length() ?: -1) != 0,
                "دفعات دون مرجع فاتورة")
            block((paymentLinks.optJSONArray("unresolvedReferenceRows")?.length() ?: -1) != 0,
                "دفعات تشير إلى فواتير غير معروفة")
            block((paymentLinks.optJSONArray("crossCurrencyPaymentRows")?.length() ?: -1) != 0,
                "دفعات بعملة مختلفة تحتاج مطابقة سعر الصرف")
        }
        val items = JSONArray()
        val keys = source.keys().asSequence().toList().sorted()
        for (key in keys) {
            val parts = key.split("|")
            require(parts.size == 2 && parts[0] in setOf("drawer", "external", "sham", "bank")
                && parts[1] in setOf("USD", "TRY", "SYP")) {
                "خزنة أو عملة غير مدعومة في الأرشيف: $key"
            }
            val amount = source.optString(key).toBigDecimalOrNull()
                ?: throw IllegalStateException("رصيد خزنة غير صالح: $key")
            require(amount.stripTrailingZeros().scale() <= 2) {
                "رصيد خزنة بدقة غير مدعومة: $key"
            }
            val minor = amount.movePointRight(2).longValueExact()
            require(minor >= 0L) { "رصيد خزنة سالب: $key" }
            val existing = store.balance(parts[0], Currency.valueOf(parts[1]))
            block(existing != 0L, "الخزنة $key لديها رصيد تشغيلي سابق")
            items.put(JSONObject()
                .put("vault", parts[0])
                .put("currency", parts[1])
                .put("sourceSignedBalance", amount.toPlainString())
                .put("sourceMinorUnits", minor)
                .put("activeMinorUnits", existing)
                .put("physicallyCounted", false))
        }
        return JSONObject()
            .put("balances", items)
            .put("blockers", blockers)
            .put("blockerCount", blockers.length())
            .put("physicalCountVerified", false)
            .put("financialPosting", "NONE")
            .put("activationPermitted", false)
            .put("reason", "لا تُفعّل الأرصدة قبل مطابقة النقد الفعلي والإقفالات والديون")
    }
}
