package com.alsafa.native8

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.alsafa.native8.data.migration.LegacyReadRepository
import com.alsafa.native8.data.migration.LegacyStagingDatabase

/** Temporary products read-only screen; final layout must be ported from v3.9.359. */
class ProductsActivity : Activity() {
    private lateinit var output: TextView
    private var offset = 0
    private val pageSize = 30
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        }
        layout.addView(TextView(this).apply {
            text = "المنتجات"
            textSize = 22f
            gravity = Gravity.CENTER
        })
        output = TextView(this).apply { text = "جاري قراءة المنتجات..." }
        layout.addView(ScrollView(this).apply { addView(output) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(Button(this).apply {
            text = "التالي"
            setOnClickListener { offset += pageSize; load() }
        })
        layout.addView(Button(this).apply {
            text = "السابق"
            setOnClickListener { offset = (offset - pageSize).coerceAtLeast(0); load() }
        })
        setContentView(layout)
        load()
    }
    private fun load() {
        Thread {
            val message = try {
                val db = LegacyStagingDatabase(applicationContext)
                try {
                    val repo = LegacyReadRepository(db)
                    val run = repo.latestRunId()
                    if (run == null) "لم تتم استعادة نسخة احتياطية بعد"
                    else {
                        val page = repo.page(run, "products", offset, pageSize)
                        buildString {
                            append("العدد: ").append(page.total).append("\n\n")
                            page.rows.forEach { row ->
                                append(row.fields.optString("name", "—")).append("\n")
                                append("ID: ").append(row.id ?: "—").append("\n")
                                if (row.fields.has("price"))
                                    append("السعر: ").append(row.fields.opt("price")).append("\n")
                                if (row.fields.has("quantity"))
                                    append("الكمية: ").append(row.fields.opt("quantity")).append("\n")
                                append("────────────\n")
                            }
                            if (page.rows.isEmpty()) append("لا توجد منتجات في هذه الصفحة")
                        }
                    }
                } finally { db.close() }
            } catch (e: Exception) { "خطأ في قراءة المنتجات: " + (e.message ?: "غير معروف") }
            runOnUiThread { output.text = message }
        }.start()
    }
}
