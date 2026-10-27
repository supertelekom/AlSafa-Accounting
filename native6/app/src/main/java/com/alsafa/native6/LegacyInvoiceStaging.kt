package com.alsafa.native6

import android.content.ContentValues
import org.json.JSONObject

/**
 * Durable, idempotent invoice staging. This never writes the live invoice,
 * stock, receipt, or journal tables. Activation requires a separate balanced
 * and atomic migration.
 */
object LegacyInvoiceStaging {
    private const val table = "legacy_invoice_staging"
    private const val ddl = """CREATE TABLE IF NOT EXISTS legacy_invoice_staging(
        target_id TEXT PRIMARY KEY NOT NULL,
        source_hash TEXT NOT NULL,
        source_section TEXT NOT NULL CHECK(source_section IN ('sales','purchases')),
        source_id TEXT NOT NULL,
        source_number TEXT NOT NULL,
        target_number INTEGER NOT NULL UNIQUE,
        invoice_date TEXT NOT NULL,
        currency TEXT NOT NULL CHECK(currency IN ('USD','TRY','SYP')),
        total_minor INTEGER NOT NULL CHECK(total_minor>=0),
        paid_minor INTEGER NOT NULL CHECK(paid_minor>=0),
        outstanding_minor INTEGER NOT NULL CHECK(outstanding_minor>=0),
        payload TEXT NOT NULL,
        UNIQUE(source_hash,source_section,source_id),
        CHECK(total_minor=paid_minor+outstanding_minor)
    )"""

    private fun minor(value:String):Long =
        value.toBigDecimal().movePointRight(2).longValueExact()

    fun stage(store:Store):JSONObject = store.transaction {
        val plan = LegacyInvoiceMigrationPlan.inspect(store)
        require(plan.optBoolean("identitySafe")) {
            "يجب إصلاح تعارضات ومعرفات الفواتير القديمة قبل تجهيز الترحيل"
        }
        val sourceHash = plan.getString("sourceSha256")
        require(sourceHash.isNotBlank()) { "بصمة النسخة القديمة مفقودة" }
        val mappings = plan.getJSONObject("invoiceMappings")
        val db = store.db
        db.execSQL(ddl)
        db.execSQL("CREATE TRIGGER IF NOT EXISTS legacy_invoice_staging_no_update BEFORE UPDATE ON legacy_invoice_staging BEGIN SELECT RAISE(ABORT,'Immutable staged invoice'); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS legacy_invoice_staging_no_delete BEFORE DELETE ON legacy_invoice_staging BEGIN SELECT RAISE(ABORT,'Immutable staged invoice'); END")
        val staged = store.scalar("SELECT count(*) FROM $table")
        if (staged != 0L) {
            require(store.scalar("SELECT count(*) FROM $table WHERE source_hash=?",sourceHash)==staged) {
                "هناك فواتير مجهزة من نسخة أخرى؛ تم إيقاف العملية"
            }
            require(staged == plan.getInt("invoiceCount").toLong()) {
                "تجهيز سابق غير مكتمل؛ لن نضيف سجلات مكررة"
            }
            for (section in listOf("sales","purchases")) {
                val list = mappings.getJSONArray(section)
                for (i in 0 until list.length()) {
                    val m = list.getJSONObject(i)
                    val existing = store.rows("SELECT source_hash,source_section,source_id,source_number,invoice_date,currency,target_number,total_minor,paid_minor,outstanding_minor FROM $table WHERE target_id=?",
                        m.getString("targetId")).singleOrNull()
                    require(existing != null &&
                        existing.getValue("source_hash")==sourceHash &&
                        existing.getValue("source_section")==section &&
                        existing.getValue("source_id")==m.getString("sourceId") &&
                        existing.getValue("source_number")==m.getString("sourceNumber") &&
                        existing.getValue("invoice_date")==m.getString("date") &&
                        existing.getValue("currency")==m.getString("currency") &&
                        existing.getValue("target_number").toLong()==m.getLong("targetNumber") &&
                        existing.getValue("total_minor").toLong()==minor(m.getString("total")) &&
                        existing.getValue("paid_minor").toLong()==minor(m.getString("paid")) &&
                        existing.getValue("outstanding_minor").toLong()==minor(m.getString("outstanding"))) {
                        "سجل مجهز لا يطابق النسخة؛ لا يمكن إعادة التجهيز"
                    }
                }
            }
            return@transaction JSONObject().put("staged",staged).put("alreadyStaged",true)
                .put("posted",false).put("sourceSha256",sourceHash)
        }
        var count = 0
        for (section in listOf("sales","purchases")) {
            val source = store.legacyRows(section).associateBy { it.getValue("legacy_id") }
            val list = mappings.getJSONArray(section)
            for (i in 0 until list.length()) {
                val m = list.getJSONObject(i)
                val id = m.getString("sourceId")
                val payload = source[id]?.getValue("payload")
                    ?: error("الفاتورة القديمة غير موجودة في الأرشيف: $section/$id")
                val total = minor(m.getString("total"))
                val paid = minor(m.getString("paid"))
                val due = minor(m.getString("outstanding"))
                require(total>0 && paid>=0 && due>=0 && Math.addExact(paid,due)==total) {
                    "مبالغ الفاتورة غير صالحة: $section/$id"
                }
                val values = ContentValues().apply {
                    put("target_id",m.getString("targetId"))
                    put("source_hash",sourceHash)
                    put("source_section",section)
                    put("source_id",id)
                    put("source_number",m.getString("sourceNumber"))
                    put("target_number",m.getLong("targetNumber"))
                    put("invoice_date",m.getString("date"))
                    put("currency",m.getString("currency"))
                    put("total_minor",total)
                    put("paid_minor",paid)
                    put("outstanding_minor",due)
                    put("payload",payload)
                }
                db.insertOrThrow(table,null,values)
                count++
            }
        }
        require(count==plan.getInt("invoiceCount")) { "عدد الفواتير المجهزة غير مطابق" }
        JSONObject().put("staged",count).put("alreadyStaged",false)
            .put("posted",false).put("sourceSha256",sourceHash)
    }
}
