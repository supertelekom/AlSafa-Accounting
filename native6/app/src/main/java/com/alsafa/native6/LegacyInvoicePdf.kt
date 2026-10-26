package com.alsafa.native6

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import org.json.JSONObject
import java.io.OutputStream

/** Historical invoice PDF: source snapshot only; never creates accounting entries. */
class LegacyInvoicePdf(private val context: Context, private val store: Store) {
    fun write(section: String, archiveKey: String, output: OutputStream) {
        val detail = LegacyInvoiceSnapshot.detail(store, section, archiveKey)
            ?: error("الفاتورة التاريخية غير موجودة")
        val invoice = detail.getJSONObject("invoice")
        val currency = invoice.optString("currency", "USD")
        val rows = mutableListOf<Pair<String, String>>()
        rows.add("رقم الفاتورة" to invoice.optString("invoiceNo"))
        rows.add("التاريخ" to invoice.optString("date"))
        rows.add("العميل / المورد" to invoice.optString("customer").ifBlank {
            invoice.optString("customerName").ifBlank { invoice.optString("supplierName") }
        })
        val phone = invoice.optString("phone").ifBlank { invoice.optString("customerPhone") }
        if (phone.isNotBlank()) rows.add("الهاتف" to phone)
        val items = invoice.optJSONArray("items")
        if (items != null) for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val name = item.optString("name").ifBlank { item.optString("productName") }
            val qty = item.optString("quantity").ifBlank { item.optString("qty", "1") }
            val price = item.optString("price").ifBlank { item.optString("unitPrice") }
            rows.add("المنتج" to name)
            rows.add("الكمية / السعر" to (qty + if (price.isBlank()) "" else " × " + price))
        }
        rows.add("الإجمالي" to (invoice.optString("total") + " " + currency))
        rows.add("المدفوع حتى الاستعادة" to (invoice.optString("paid") + " " + currency))
        val total = invoice.optString("total", "0").toBigDecimal()
        val paid = invoice.optString("paid", "0").toBigDecimal()
        rows.add("المتبقي حتى الاستعادة" to (total - paid).toPlainString() + " " + currency)
        rows.add("حالة السجل" to "فاتورة تاريخية - قراءة فقط")
        val font = runCatching { Typeface.createFromAsset(context.assets, "Tajawal-Medium.ttf") }
            .getOrDefault(Typeface.DEFAULT)
        val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
            typeface = font
        }
        val width = 320
        val textWidth = 272
        fun layout(value: String): StaticLayout = StaticLayout.Builder
            .obtain(value, 0, value.length, paint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false).build()
        val title = "الصفا للساعات"
        val lines = rows.flatMap { listOf(layout(it.first), layout(it.second)) }
        val height = 115 + lines.sumOf { it.height + 5 } + 45
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            var y = 24f
            val header = layout(title)
            canvas.save(); canvas.translate(24f, y); header.draw(canvas); canvas.restore()
            y += header.height + 15
            for (line in lines) {
                canvas.save(); canvas.translate(24f, y); line.draw(canvas); canvas.restore()
                y += line.height + 5
            }
            document.finishPage(page)
            document.writeTo(output)
        } finally {
            document.close()
        }
    }
}
