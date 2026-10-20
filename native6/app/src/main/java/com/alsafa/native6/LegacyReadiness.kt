package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only inspection of a staged v3.9.x backup.
 * No amounts are converted and no legacy records are posted to Native 6.
 */
object LegacyReadiness {
    private val core = listOf("products", "sales", "repairs", "suppliers", "customers", "expenses", "purchases", "maintenanceParts")
    private val identityFields = listOf("id", "uuid", "invoiceId", "invoice_id")

    fun inspect(payload: String): String {
        val root=JSONObject(payload)
        val data=if(root.optString("format")=="alsafa-accounting-backup") root.getJSONObject("data") else root
        val lines=mutableListOf<String>()
        lines += "فحص قراءة فقط — لم تُعدّل الحسابات أو المخزون"
        lines += "أقسام النسخة: ${data.length()}"
        core.forEach { key ->
            val arr=data.optJSONArray(key)
            if(arr==null) {
                lines += "$key: القسم غير موجود أو ليس قائمة"
            } else {
                lines += "$key: ${arr.length()} سجل"
                val ids=HashSet<String>()
                var duplicate=0
                var missing=0
                var nonObjects=0
                val keys=linkedSetOf<String>()
                for(i in 0 until arr.length()) {
                    val row=arr.optJSONObject(i)
                    if(row==null) {nonObjects++;continue}
                    val idField=identityFields.firstOrNull { row.has(it) && !row.isNull(it) }
                    if(idField==null || row.optString(idField).isBlank()) missing++
                    else if(!ids.add(row.optString(idField))) duplicate++
                    if(i<20) row.keys().forEachRemaining { keys.add(it) }
                }
                if(key=="sales" && arr.length()>0) {
                    var invalid=0
                    for(i in 0 until arr.length()) {
                        val row=arr.optJSONObject(i) ?: continue
                        if(row.has("total") && !row.isNull("total") &&
                            row.optString("total").toBigDecimalOrNull()==null) invalid++
                    }
                    if(invalid>0) lines += "  إجمالي مبيعات غير رقمي: $invalid"
                }
                if(duplicate>0) lines += "  معرفات مكررة: $duplicate"
                if(missing>0) lines += "  سجلات بلا معرف معروف: $missing (قد تستخدم النسخة تسمية مختلفة)"
                if(nonObjects>0) lines += "  سجلات غير صالحة ككائنات: $nonObjects"
                lines += "  حقول عينة: ${keys.take(15).joinToString("، ")}"
            }
        }
        lines += "لا يمكن اعتماد ترحيل الأرصدة قبل مطابقة الديون والفواتير والمخزون مع الأصل."
        return lines.joinToString("\n")
    }
}
