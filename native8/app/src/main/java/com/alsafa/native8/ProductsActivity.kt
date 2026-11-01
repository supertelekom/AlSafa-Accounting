package com.alsafa.native8

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.alsafa.native8.data.migration.LegacyReadRepository
import com.alsafa.native8.data.migration.LegacyStagingDatabase

/** Read-only preview. Replace its visual layout with the exact legacy design. */
class ProductsActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var items: LinearLayout
    private lateinit var next: Button
    private lateinit var previous: Button
    private var offset = 0
    private val pageSize = 30
    private var total = 0
    private var busy = false
    private var requestSerial = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "المنتجات"
            textSize = 22f
            gravity = Gravity.CENTER
        })
        status = TextView(this).apply { text = "جاري قراءة المنتجات..." }
        root.addView(status)
        items = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(ScrollView(this).apply { addView(items) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        next = Button(this).apply {
            text = "التالي"
            setOnClickListener {
                if (!busy && offset + pageSize < total) {
                    offset += pageSize
                    load()
                }
            }
        }
        previous = Button(this).apply {
            text = "السابق"
            setOnClickListener {
                if (!busy && offset > 0) {
                    offset = (offset - pageSize).coerceAtLeast(0)
                    load()
                }
            }
        }
        root.addView(next)
        root.addView(previous)
        setContentView(root)
        load()
    }

    private fun load() {
        if (busy) return
        busy = true
        val serial = ++requestSerial
        val requestedOffset = offset
        next.isEnabled = false
        previous.isEnabled = false
        status.text = "جاري قراءة المنتجات..."
        Thread {
            val result = try {
                val db = LegacyStagingDatabase(applicationContext)
                try {
                    val repo = LegacyReadRepository(db)
                    val run = repo.latestRunId()
                    if (run == null) null
                    else run to repo.page(run, "products", requestedOffset, pageSize)
                } finally { db.close() }
            } catch (e: Exception) {
                runOnUiThread {
                    if (serial == requestSerial && !isFinishing && !isDestroyed) {
                        busy = false
                        status.text = "خطأ في قراءة المنتجات: " + (e.message ?: "غير معروف")
                        updateNavigation()
                    }
                }
                return@Thread
            }
            runOnUiThread {
                if (serial != requestSerial || isFinishing || isDestroyed) return@runOnUiThread
                busy = false
                items.removeAllViews()
                if (result == null) {
                    total = 0
                    status.text = "لم تتم استعادة نسخة احتياطية بعد"
                    updateNavigation()
                    return@runOnUiThread
                }
                val (runId, page) = result
                total = page.total
                if (offset >= total && offset > 0) offset = 0
                status.text = "العدد: $total"
                for (row in page.rows) {
                    val id = row.id
                    items.addView(Button(this).apply {
                        val product = row.fields
                        text = buildString {
                            append(product.optString("name", "—"))
                            if (product.has("stock")) append(" | ").append(product.opt("stock"))
                            if (product.has("salePrice")) append(" | ").append(product.opt("salePrice"))
                            if (product.has("currency")) append(" ").append(product.optString("currency"))
                        }
                        isEnabled = !id.isNullOrBlank()
                        setOnClickListener {
                            if (!id.isNullOrBlank()) {
                                startActivity(Intent(this@ProductsActivity, ProductDetailsActivity::class.java).apply {
                                    putExtra(ProductDetailsActivity.EXTRA_RUN_ID, runId)
                                    putExtra(ProductDetailsActivity.EXTRA_PRODUCT_ID, id)
                                })
                            }
                        }
                    })
                }
                if (page.rows.isEmpty()) {
                    items.addView(TextView(this).apply { text = "لا توجد منتجات في هذه الصفحة" })
                }
                updateNavigation()
            }
        }.start()
    }

    private fun updateNavigation() {
        next.isEnabled = !busy && offset + pageSize < total
        previous.isEnabled = !busy && offset > 0
    }
}
