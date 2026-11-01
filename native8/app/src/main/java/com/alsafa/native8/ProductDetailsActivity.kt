package com.alsafa.native8

import android.app.Activity
import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.ImageView
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

    private lateinit var content: LinearLayout

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
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(title)
        content.addView(details)
        root.addView(ScrollView(this).apply { addView(content) },
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
                    val repository = LegacyReadRepository(db)
                    val row = repository.byOriginalId(runId, "products", productId)
                    if (row == null) null
                    else row.fields to repository.productVariants(runId, row.fields.optString("variantGroupId"))
                } finally { db.close() }
            } catch (e: Exception) {
                runOnUiThread { details.text = "تعذر قراءة المنتج: " + (e.message ?: "غير معروف") }
                return@Thread
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                content.removeAllViews()
                if (message == null) {
                    addText("لم يُعثر على المنتج في النسخة المستوردة")
                } else {
                    val product = message.first
                    addText(describe(product, productId))
                    for (field in listOf("photo", "photo2", "photo3", "barcodePhoto")) {
                        val source = product.optString(field)
                        if (source.isNotBlank()) addImage(source)
                    }
                    if (message.second.isNotEmpty()) {
                        addText("الألوان المرتبطة: " + message.second.size)
                        message.second.forEach { variant ->
                            val v = variant.fields
                            addText(v.optString("variantColor", v.optString("name")) +
                                " | " + v.opt("stock") + " | " + v.opt("salePrice") +
                                " " + v.optString("currency") + "\\nID: " + variant.id)
                        }
                    }
                }
            }
        }.start()
    }

    private fun addText(value: String) {
        content.addView(TextView(this).apply {
            text = value
            textSize = 16f
            setPadding(4, 12, 4, 12)
        })
    }

    private fun addImage(source: String) {
        if (!source.startsWith("data:image/") || !source.contains(";base64,")) return
        val preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "صورة المنتج"
        }
        content.addView(preview, LinearLayout.LayoutParams(-1, 320))
        Thread {
            val bitmap = try { decodeImage(source, 800) } catch (_: Exception) { null }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (bitmap == null) {
                    preview.contentDescription = "تعذر عرض صورة المنتج"
                    return@runOnUiThread
                }
                preview.setImageBitmap(bitmap)
                preview.setOnClickListener {
                    Thread {
                        val full = try { decodeImage(source, 1600) } catch (_: Exception) { null }
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed && full != null) {
                                AlertDialog.Builder(this@ProductDetailsActivity)
                                    .setView(ImageView(this@ProductDetailsActivity).apply {
                                        setImageBitmap(full)
                                        adjustViewBounds = true
                                    })
                                    .setPositiveButton("إغلاق", null)
                                    .show()
                            }
                        }
                    }.start()
                }
            }
        }.start()
    }

    private fun decodeImage(source: String, maxDimension: Int): android.graphics.Bitmap? {
        val bytes = Base64.decode(source.substringAfter(";base64,"), Base64.DEFAULT)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDimension * 2 ||
               bounds.outHeight / sample > maxDimension * 2) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
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
