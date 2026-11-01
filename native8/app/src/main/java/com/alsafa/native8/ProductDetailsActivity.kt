package com.alsafa.native8

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.alsafa.native8.data.migration.LegacyReadRepository
import com.alsafa.native8.data.migration.LegacyStagingDatabase
import org.json.JSONObject

/** Read-only details of an original product; never edits inventory or prices. */
class ProductDetailsActivity : Activity() {
    companion object {
        const val EXTRA_RUN_ID = "legacy_run_id"
        const val EXTRA_PRODUCT_ID = "legacy_product_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(28, 28, 28, 28)
        }
        val title = TextView(this).apply {
            text = "تفاصيل المنتج"
            textSize = 22f
            gravity = Gravity.CENTER
        }
        val details = TextView(this).apply { text = "جاري قراءة المنتج..." ; textSize = 16f }
        root.addView(title)
        root.addView(ScrollView(this).apply { addView(details) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        val runId = intent.getLongExtra(EXTRA_RUN_ID, -1L)
        val productId = intent.getStringExtra(EXTRA_PRODUCT_ID)
        if (runId <= 0L || productId.isNullOrBlank()) {
            details.text = "معرف المنتج غير موجود"
            return
        }
        Thread {
            val message = try {
                val db = LegacyStagingDatabase(applicationContext)
                try {
                    val row = LegacyReadRepository(db).byOriginalId(runId, "products", productId)
                    if (row == null) "لم يُعثر على المنتج في النسخة المستوردة"
                    else describe(row.fields, productId)
                } finally { db.close() }
            } catch (e: Exception) { "تعذر قراءة المنتج: " + (e.message ?: "غير معروف") }
            runOnUiThread { details.text = message }
        }.start()
    }

    private fun describe(p: JSONObject, originalId: String): String = buildString {
        append(p.optString("name", "—")).append("\n\n")
        append("المعرف: ").append(originalId).append("\n")
        for ((label, field) in listOf(
            "الماركة" to "brand", "الموديل" to "model",
            "الباركود" to "barcode", "الكمية" to "stock",
            "سعر البيع" to "salePrice", "التكلفة" to "cost",
            "العملة" to "currency", "اللون" to "variantColor",
            "مجموعة الألوان" to "variantGroupId", "الوصف" to "description",
            "الملاحظات" to "notes"
        )) {
            if (p.has(field) && !p.isNull(field))
                append(label).append(": ").append(p.opt(field)).append("\n")
        }
        for (field in listOf("photo", "photo2", "photo3", "barcodePhoto")) {
            val value = p.optString(field)
            if (value.isNotBlank()) {
                append("الصورة ").append(field).append(": محفوظة ضمن السجل")
                    .append(" (العرض قيد التطوير)\n")
            }
        }
        val lots = p.optJSONArray("fundingLots")
        if (lots != null) {
            append("\nدفعات التمويل: ").append(lots.length()).append("\n")
            for (i in 0 until lots.length()) {
                val lot = lots.optJSONObject(i) ?: continue
                append("• ").append(lot.optString("id"))
                    .append(" | المتبقي: ").append(lot.opt("qtyRemaining"))
                    .append(" | التكلفة: ").append(lot.opt("unitCost"))
                    .append(" ").append(lot.optString("currency")).append("\n")
            }
        }
    }
