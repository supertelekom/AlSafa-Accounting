package com.alsafa.native6

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Tablet-first native entry screen. Every operational destination opens its existing
 * SectionActivity and therefore retains the current SQLite-backed business logic.
 */
class TabletDashboardActivity : Activity() {
    private val ink = Color.rgb(58, 48, 39)
    private val gold = Color.rgb(166, 116, 38)
    private val paper = Color.rgb(248, 244, 236)
    private var mode = "home"
    private lateinit var font: Typeface

    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
    private fun shape(fill: Int, stroke: Int = Color.rgb(226, 211, 188)) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(17).toFloat()
            setStroke(dp(1), stroke)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        font = try { Typeface.createFromAsset(assets, "Tajawal-Medium.ttf") }
        catch (_: Exception) { Typeface.DEFAULT }
        mode = savedInstanceState?.getString("mode") ?: "home"
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("mode", mode)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (mode != "home") { mode = "home"; render() } else super.onBackPressed()
    }

    private fun title(text: String, size: Float, color: Int = ink) =
        TextView(this).apply {
            this.text = text
            textSize = size
            typeface = font
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

    private fun open(page: String) {
        if (page == "camera") startActivity(Intent(this, MediaCaptureActivity::class.java))
        else if (page == "barcode") startActivityForResult(Intent(this, BarcodeScanActivity::class.java), 64)
        else startActivity(Intent(this, SectionActivity::class.java).putExtra(MainActivity.EXTRA_PAGE, page))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 64 && resultCode == RESULT_OK) {
            val barcode = data?.getStringExtra(BarcodeScanActivity.EXTRA_BARCODE).orEmpty()
            if (barcode.isNotBlank()) AlertDialog.Builder(this)
                .setTitle("تمت قراءة الباركود")
                .setMessage(barcode)
                .setPositiveButton("المنتجات") { _, _ -> open("products") }
                .setNeutralButton("المبيع") { _, _ -> open("sale") }
                .setNegativeButton("إغلاق", null)
                .show()
        }
    }

    private fun tile(label: String, detail: String, symbol: String, action: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(17), dp(12), dp(17), dp(12))
            background = shape(Color.WHITE)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            elevation = dp(2).toFloat()
        }
        box.addView(title(symbol, 27f, gold))
        box.addView(title(label, 19f))
        box.addView(title(detail, 13f, Color.rgb(113, 101, 87)))
        return box
    }

    private fun render() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(paper)
            setPadding(dp(18), dp(12), dp(18), dp(10))
        }
        setContentView(outer)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        outer.addView(header)
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        brand.addView(title("الصفا للساعات", 29f, gold))
        brand.addView(title("نظام الإدارة والمحاسبة · يعمل محلياً دون إنترنت", 13f))
        if (mode != "home") {
            header.addView(Button(this).apply {
                text = "الرئيسية"
                isAllCaps = false
                typeface = font
                setOnClickListener { mode = "home"; render() }
            })
        }
        val separator = View(this).apply { setBackgroundColor(Color.rgb(226, 211, 188)) }
        outer.addView(separator, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(10); bottomMargin = dp(14)
        })
        val scroll = ScrollView(this).apply { isFillViewport = true }
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        scroll.addView(content)
        when (mode) {
            "home" -> {
                content.addView(title("مرحباً بك في الصفا", 25f))
                content.addView(title("اختر طريقة العمل المناسبة للوردية", 16f))
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })
                row.addView(tile("الوضع المبسط", "المبيع والصيانة والفواتير والإقفال", "⚡") {
                    mode = "simple"; render()
                }, LinearLayout.LayoutParams(0, dp(195), 1f).apply { marginEnd = dp(12) })
                row.addView(tile("الوضع الكامل", "المخزون والمحاسبة والموردون والتقارير", "◆") {
                    mode = "advanced"; render()
                }, LinearLayout.LayoutParams(0, dp(195), 1f))
                content.addView(title("وصول سريع", 21f, gold))
                grid(content, listOf(
                    Triple("المبيع", "فاتورة جديدة", "sale"),
                    Triple("الصيانة", "استلام وتسليم", "repairs"),
                    Triple("المنتجات", "الأصناف والمخزون", "products"),
                    Triple("الفواتير", "سجل المبيع والصيانة", "invoices"),
                    Triple("الموردون والمشتريات", "المشتريات والذمم", "suppliers"),
                    Triple("الإقفال اليومي", "الخزنات والأرصدة", "vaults"),
                    Triple("الكاميرا والمعرض", "حفظ صور محلية", "camera"),
                    Triple("قارئ الباركود", "قراءة بكاميرا الجهاز", "barcode")
                ))
            }
            "simple" -> {
                content.addView(title("العمل السريع", 25f, gold))
                grid(content, listOf(
                    Triple("مبيع", "إصدار فاتورة", "sale"),
                    Triple("الصيانة", "استلام وتسليم", "repairs"),
                    Triple("الفواتير", "القبض والمرتجعات", "invoices"),
                    Triple("الخزنات", "الإقفال اليومي", "vaults")
                ))
            }
            else -> {
                content.addView(title("إدارة المحل", 25f, gold))
                grid(content, listOf(
                    Triple("المبيع", "فواتير البيع", "sale"),
                    Triple("المنتجات", "الأصناف وقطع الصيانة", "products"),
                    Triple("الفواتير", "القبض والمرتجعات", "invoices"),
                    Triple("الصيانة", "الاستلام والتسليم", "repairs"),
                    Triple("الخزنات", "الأرصدة والإقفال", "vaults"),
                    Triple("الموردون", "المشتريات والديون", "suppliers"),
                    Triple("التقارير", "تقرير شهر محدد", "reports"),
                    Triple("تقارير الفترة", "بين تاريخين", "period-reports"),
                    Triple("الشركاء", "الأرباح والسحوبات", "partners"),
                    Triple("المخزون", "التقييم والتنبيهات", "inventory"),
                    Triple("سجل العمليات", "تدقيق العمليات", "audit"),
                    Triple("النسخ الاحتياطي", "التصدير والاستعادة", "backup")
                ))
            }
        }
    }

    private fun grid(parent: LinearLayout, items: List<Triple<String, String, String>>) {
        val columns = if (resources.configuration.smallestScreenWidthDp >= 840) 4 else 2
        items.chunked(columns).forEach { group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            group.forEach { (label, detail, route) ->
                row.addView(tile(label, detail, "◈") { open(route) },
                    LinearLayout.LayoutParams(0, dp(142), 1f).apply {
                        marginEnd = dp(10)
                    })
            }
            repeat(columns - group.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
        }
    }
}
