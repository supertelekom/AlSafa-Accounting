package com.alsafa.native8

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.alsafa.native8.data.migration.LegacyReadRepository
import com.alsafa.native8.data.migration.LegacyStagingDatabase
import com.alsafa.native8.sales.AppDatabase
import kotlinx.coroutines.runBlocking
import com.alsafa.native8.data.operational.RoomProductImportPromoter
import com.alsafa.native8.data.operational.RoomInventoryRepository
import com.alsafa.native8.data.operational.RoomProductSnapshotAudit
import com.alsafa.native8.data.operational.RoomProductEditRepository
import com.alsafa.native8.data.operational.RoomProductStockRepository

/** Read-only preview. Replace its visual layout with the exact legacy design. */
class ProductsActivity : Activity() {
    private data class ProductDisplayRow(val id: String, val ordinal: Int, val fields: org.json.JSONObject)
    private data class ProductDisplayPage(val rows: List<ProductDisplayRow>, val total: Int, val sourceSha256: String)
    private lateinit var status: TextView
    private lateinit var items: LinearLayout
    private lateinit var databaseStatus: TextView
    private var diagnostic = "فحص قاعدة البيانات لم يكتمل بعد"
    private lateinit var next: Button
    private lateinit var previous: Button
    private var offset = 0
    private val pageSize = 30
    private var total = 0
    private var busy = false
    private var requestSerial = 0
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun productRow(textValue: String): TextView = TextView(this).apply {
        text = textValue
        textSize = 15f
        gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG_RTL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        minHeight = dp(56)
        maxLines = 3
        ellipsize = android.text.TextUtils.TruncateAt.END
        background = GradientDrawable().apply {
            setColor(Color.rgb(245, 246, 248))
            cornerRadius = dp(8).toFloat()
        }
        isClickable = true
        isFocusable = true
    }
    private fun rowParams() = LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = dp(8)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the activity inside the usable display area, including system bars.
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(true)
        val root = FrameLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            fitsSystemWindows = true
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "المنتجات"
            textSize = 17f
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        status = TextView(this).apply {
            text = "جاري قراءة المنتجات..."
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        header.addView(status, LinearLayout.LayoutParams(0, dp(42), 1f))
        toolbar.addView(header)
        databaseStatus = TextView(this).apply {
            text = "جارٍ التحقق من قاعدة البيانات..."
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        actions.addView(Button(this).apply {
            text = "إضافة منتج"
            textSize = 13f
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            setOnClickListener {
                startActivity(Intent(this@ProductsActivity, ProductEditorActivity::class.java))
            }
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        actions.addView(Button(this).apply {
            text = "فحص القاعدة"
            textSize = 12f
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            setOnClickListener {
                AlertDialog.Builder(this@ProductsActivity)
                    .setTitle("نتيجة فحص SQLite")
                    .setMessage(diagnostic)
                    .setPositiveButton("إغلاق", null)
                    .show()
            }
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        toolbar.addView(actions)
        root.addView(toolbar, FrameLayout.LayoutParams(-1, dp(86), Gravity.TOP))
        items = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(ScrollView(this).apply {
            isFillViewport = false
            addView(items)
            clipToPadding = true
            isVerticalScrollBarEnabled = true
        }, FrameLayout.LayoutParams(-1, -1).apply {
            topMargin = dp(90)
            bottomMargin = dp(52)
        })
        next = Button(this).apply {
            text = "التالي"
            textSize = 14f
            minHeight = 0
            minimumHeight = 0
            setOnClickListener {
                if (!busy && offset + pageSize < total) {
                    offset += pageSize
                    load()
                }
            }
        }
        previous = Button(this).apply {
            text = "السابق"
            textSize = 14f
            minHeight = 0
            minimumHeight = 0
            setOnClickListener {
                if (!busy && offset > 0) {
                    offset = (offset - pageSize).coerceAtLeast(0)
                    load()
                }
            }
        }
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(previous, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(next, LinearLayout.LayoutParams(0, dp(44), 1f))
        }
        root.addView(navigation, FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM))
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
                val staging = LegacyStagingDatabase(applicationContext)
                try {
                    val legacy = LegacyReadRepository(staging)
                    val run = legacy.latestRunId()
                    if (run == null) null
                    else runBlocking {
                        val room = AppDatabase.get(applicationContext)
                        RoomProductImportPromoter(staging, room).promote(run)
                        val repo = RoomInventoryRepository(room)
                        val dao = room.operationalInventoryWriteDao()
                        val source = dao.sourceForRun(run) ?: error("لم يتم العثور على مصدر المنتجات")
                        val rows = repo.page(source, requestedOffset, pageSize)
                        val stockRepo = RoomProductStockRepository(room)
                        val resolved = rows.map { row ->
                            val product = row.data
                            product.put("stock", stockRepo.currentStock(source, row.id))
                            ProductDisplayRow(row.id, row.ordinal, product)
                        }
                        val page = ProductDisplayPage(resolved, dao.productCount(source), source)
                        val audit = RoomProductSnapshotAudit(staging, room).inspect(run, source)
                        val locals = RoomProductEditRepository(room).localProducts()
                        Pair(Triple(run, page, audit), locals)
                    }
                } finally { staging.close() }
            } catch (e: Exception) {
                runOnUiThread {
                    if (serial == requestSerial && !isFinishing && !isDestroyed) {
                        busy = false
                        status.text = "خطأ في قراءة المنتجات: " + (e.message ?: "غير معروف")
                        databaseStatus.text = "تعذر فحص SQLite التشغيلية"
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
                val (snapshot, localProducts) = result
                val (runId, page, audit) = snapshot
                val stagedCount = audit.staged
                val localCount = localProducts.size
                val operationalTotal = page.total + localCount
                diagnostic = "المصدر: alsafa_native8_operational.db\nالمنتجات التشغيلية: " + page.total + "\nسجلات الاستيراد: " + stagedCount + "\n" + (if (page.total == stagedCount) "العدد متطابق" else "تحذير: العدد مختلف")
                diagnostic += "\nاختلافات المحتوى: " + audit.mismatches + "\n" + audit.examples.joinToString("\n")
                databaseStatus.text = "SQLite: " + operationalTotal + " | المستورد: " + page.total + " | " + (if (audit.matches) "تطابق كامل" else "اختلاف: " + audit.mismatches)
                total = page.total
                if (offset >= total && offset > 0) offset = 0
                status.text = "العدد الإجمالي: $operationalTotal"
                for (local in localProducts) {
                    val label = buildString {
                        append(local.optString("name", "—"))
                        append("  •  الكمية: ").append(local.optString("stock", "—").ifBlank { "—" })
                        append("  •  البيع: ").append(local.optString("salePrice", "—").ifBlank { "—" })
                        append("  •  التكلفة: ").append(local.optString("cost", "—").ifBlank { "—" })
                        append(" ").append(local.optString("currency"))
                    }
                    items.addView(productRow(label).apply {
                        setOnClickListener {
                            startActivity(Intent(this@ProductsActivity, ProductEditorActivity::class.java).apply {
                                putExtra(ProductEditorActivity.EXTRA_ID, local.optString("id"))
                            })
                        }
                    }, rowParams())
                }
                for (row in page.rows) {
                    val id = row.id
                    val product = row.fields
                    val label = buildString {
                        append(product.optString("name", "—"))
                        if (product.has("stock")) append("  •  الكمية: ").append(product.opt("stock"))
                        if (product.has("salePrice")) append("  •  البيع: ").append(product.opt("salePrice"))
                        if (product.has("currency")) append(" ").append(product.optString("currency"))
                        append("  •  التكلفة: ").append(product.optString("cost", "—").ifBlank { "—" })
                    }
                    items.addView(productRow(label).apply {
                        isEnabled = id.isNotBlank()
                        setOnLongClickListener {
                            if (id.isNotBlank()) {
                                startActivity(Intent(this@ProductsActivity, ProductEditorActivity::class.java).apply {
                                    putExtra(ProductEditorActivity.EXTRA_SOURCE, page.sourceSha256)
                                    putExtra(ProductEditorActivity.EXTRA_ID, id)
                                })
                            }
                            true
                        }
                        setOnClickListener {
                            if (id.isNotBlank()) {
                                startActivity(Intent(this@ProductsActivity, ProductDetailsActivity::class.java).apply {
                                    putExtra(ProductDetailsActivity.EXTRA_RUN_ID, runId)
                                    putExtra(ProductDetailsActivity.EXTRA_PRODUCT_ID, id)
                                })
                            }
                        }
                    }, rowParams())
                }
                if (page.rows.isEmpty()) {
                    items.addView(TextView(this).apply { text = "لا توجد منتجات في هذه الصفحة" })
                }
                updateNavigation()
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        if (::items.isInitialized && !busy) load()
    }

    private fun updateNavigation() {
        next.isEnabled = !busy && offset + pageSize < total
        previous.isEnabled = !busy && offset > 0
    }
}
