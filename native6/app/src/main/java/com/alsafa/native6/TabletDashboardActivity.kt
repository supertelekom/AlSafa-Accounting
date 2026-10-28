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
    private val ink = Color.rgb(62, 52, 41)
    private val gold = Color.rgb(169, 121, 41)
    private val paper = Color.rgb(248, 243, 235)
    private var mode = "home"
    private lateinit var font: Typeface

    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
    private fun shape(fill: Int, stroke: Int = Color.rgb(234, 223, 206)) =
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
        if (page == "barcode") startActivityForResult(Intent(this, BarcodeScanActivity::class.java), 64)
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
        val simplified = mode == "simple"
        val chooser = mode == "home"
        val dark = chooser && label == "الوضع الكامل"
        val advisor = chooser && label == "ساعدني أختار ساعتي"
        val fill = when {
            dark -> Color.rgb(46, 40, 34)
            advisor -> Color.rgb(37, 32, 28)
            simplified -> Color.rgb(255, 250, 242)
            else -> Color.rgb(255, 253, 249)
        }
        val border = if (simplified || (chooser && label == "الوضع المبسط")) Color.rgb(216, 189, 136)
            else Color.rgb(234, 223, 206)
        val box = LinearLayout(this).apply {
            orientation = if (simplified) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(if (simplified) 18 else 14), dp(12), dp(if (simplified) 18 else 14), dp(12))
            background = GradientDrawable().apply {
                setColor(fill)
                cornerRadius = dp(if (simplified) 24 else if (advisor) 30 else if (chooser) 26 else 18).toFloat()
                setStroke(dp(if (simplified || (chooser && label == "الوضع المبسط")) 2 else 1), border)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            elevation = dp(if (chooser) 5 else 2).toFloat()
        }
        val foreground = if (dark || advisor) Color.WHITE else ink
        val symbolView = title(symbol, if (simplified) 47f else if (chooser) 30f else 25f,
            if (dark || advisor) Color.rgb(234, 217, 192) else gold).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        box.addView(symbolView, if (simplified) LinearLayout.LayoutParams(-2, dp(65))
            else LinearLayout.LayoutParams(dp(if (chooser) 58 else 46), dp(if (chooser) 58 else 46)))
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (simplified) Gravity.CENTER else Gravity.CENTER_VERTICAL
        }
        box.addView(labels, if (simplified) LinearLayout.LayoutParams(-1, -2)
            else LinearLayout.LayoutParams(0, -2, 1f))
        labels.addView(title(label, if (simplified) 24f else if (chooser) 20f else 17f, foreground).apply {
            gravity = if (simplified) Gravity.CENTER else Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
        })
        labels.addView(title(detail, 11f,
            if (dark || advisor) Color.rgb(213, 201, 187) else Color.rgb(113, 101, 87)).apply {
            gravity = if (simplified) Gravity.CENTER else Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
        })
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
                content.addView(title("ALSAFA WATCHES", 13f, gold))
                content.addView(title("كيف تريد أن تبدأ؟", 25f))
                content.addView(title("اختر بيئة العمل المناسبة، أو افتح مستشار اختيار الساعة أمام العميل.", 15f))
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })
                row.addView(tile("الوضع المبسط", "بيع وصيانة وتسليم سريع", "⚡") {
                    mode = "simple"; render()
                }, LinearLayout.LayoutParams(0, dp(144), 1f).apply { marginEnd = dp(12) })
                row.addView(tile("الوضع الكامل", "الإدارة والمخزون والحسابات", "◆") {
                    mode = "advanced"; render()
                }, LinearLayout.LayoutParams(0, dp(144), 1f))
                content.addView(tile("ساعدني أختار ساعتي", "اختيارات من الساعات المتوفرة فعلياً الآن", "✦") {
                    open("watch-advisor")
                }, LinearLayout.LayoutParams(-1, dp(112)).apply { topMargin = dp(14) })
            }
            "simple" -> {
                content.addView(title("العمل السريع", 25f, gold))
                content.addView(title("اختر العملية فقط.", 15f))
                grid(content, listOf(
                    Triple("مبيع", "بيع سريع", "simple-sale"),
                    Triple("صيانة سريعة", "استلام أو تسليم مباشر", "repairs"),
                    Triple("تسليم الساعات", "الساعات التي بقيت لدينا", "repairs"),
                    Triple("تسليم الوردية", "النقود والمحجوزات والمنتظر", "vaults")
                ))
            }
            else -> {
                content.addView(title("الوضع الكامل", 25f, gold))
                content.addView(title("كل عمليات المتجر والإدارة.", 15f))
                grid(content, listOf(
                    Triple("اليوم", "ملخص وتنبيهات العمل الآن", "reports"),
                    Triple("مبيع", "شاشة المبيع الكاملة", "sale"),
                    Triple("استلام صيانة", "طلب صيانة كامل", "repairs"),
                    Triple("تسليم صيانة", "طلبات قيد المتابعة", "repairs"),
                    Triple("قبض دين", "المبالغ المستحقة", "invoices"),
                    Triple("مصروف", "تسجيل مصروف", "vaults"),
                    Triple("بحث", "عميل، هاتف، طلب أو منتج", "invoices")
                ))
                content.addView(title("آخر العمليات", 19f, gold))
            }

        }
    }

    private fun grid(parent: LinearLayout, items: List<Triple<String, String, String>>) {
        val columns = 2
        items.chunked(columns).forEach { group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(if (mode == "simple") 14 else 11) })
            group.forEach { (label, detail, route) ->
                row.addView(tile(label, detail, "◈") { open(route) },
                    LinearLayout.LayoutParams(0, dp(if (mode == "simple") 185 else 104), 1f).apply {
                        marginEnd = dp(if (mode == "simple") 14 else 11)
                    })
            }
            repeat(columns - group.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
        }
    }
}
