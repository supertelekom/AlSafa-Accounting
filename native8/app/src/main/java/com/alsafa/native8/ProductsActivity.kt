package com.alsafa.native8

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.util.TypedValue
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(n: Int) = (n * density + 0.5f).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(12, 8, 12, 8)
        }
        root.addView(TextView(this).apply {
            text = "المنتجات"
            textSize = 18f
            gravity = Gravity.CENTER
        })
        status = TextView(this).apply { text = "جاري قراءة المنتجات..."; textSize = 14f }
        root.addView(status)
        databaseStatus = TextView(this).apply {
            text = "جارٍ التحقق من قاعدة البيانات..."
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(20, 82, 105))
            setPadding(16, 20, 16, 20)
        }
        // Keep the SQLite audit status off the customer-facing product list.
        root.addView(Button(this).apply {
            text = "فحص القاعدة"
            textSize = 13f
            minHeight = 0
            minimumHeight = 0
            setOnClickListener {
                AlertDialog.Builder(this@ProductsActivity)
                    .setTitle("نتيجة فحص SQLite")
                    .setMessage(diagnostic)
                    .setPositiveButton("إغلاق", null)
                    .show()
            }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(Button(this).apply {
            text = "إضافة منتج تجريبي"
            textSize = 14f
            minHeight = 0
            minimumHeight = 0
            setOnClickListener {
                startActivity(Intent(this@ProductsActivity, ProductEditorActivity::class.java))
            }
        }, LinearLayout.LayoutParams(-1, 44))
        items = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(ScrollView(this).apply {
            isFillViewport = false
            addView(items)
        },
            LinearLayout.LayoutParams(-1, 0, 1f))
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
            addView(previous, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(next, LinearLayout.LayoutParams(0, 44, 1f))
        }
        root.addView(navigation)
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
                    items.addView(Button(this).apply {
                        textSize = 15f
                        isAllCaps = false
                        setSingleLine(false)
                        maxLines = 2
                        gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
                        setPadding(dp(12), dp(6), dp(12), dp(6))
                        text = buildString {
                            append("منتج محلي: ").append(local.optString("name"))
                            append(" | الكمية: ").append(local.optString("stock", "—").ifBlank { "—" })
                            append(" | سعر البيع: ").append(local.optString("salePrice", "—").ifBlank { "—" })
                            append(" | رأس المال: ").append(local.optString("cost", "—").ifBlank { "—" })
                            append(" ").append(local.optString("currency"))
                        }
                        setOnClickListener {
                            startActivity(Intent(this@ProductsActivity, ProductEditorActivity::class.java).apply {
                                putExtra(ProductEditorActivity.EXTRA_ID, local.optString("id"))
                            })
                        }
                    }, LinearLayout.LayoutParams(-1, dp(72)).apply {
                        bottomMargin = dp(6)
                    })
                }
                for (row in page.rows) {
                    val id = row.id
                    items.addView(Button(this).apply {
                        textSize = 15f
                        isAllCaps = false
                        setSingleLine(false)
                        maxLines = 2
                        gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
                        setPadding(dp(12), dp(6), dp(12), dp(6))
                        val product = row.fields
                        text = buildString {
                            append(product.optString("name", "—"))
                            if (product.has("stock")) append(" | ").append(product.opt("stock"))
                            if (product.has("salePrice")) append(" | ").append(product.opt("salePrice"))
                            if (product.has("currency")) append(" ").append(product.optString("currency"))
                            append(" | رأس المال: ").append(product.optString("cost", "—").ifBlank { "—" })
                        }
                        isEnabled = !id.isNullOrBlank()
                        setOnLongClickListener {
                            if (!id.isNullOrBlank()) {
                                startActivity(Intent(this@ProductsActivity, ProductEditorActivity::class.java).apply {
                                    putExtra(ProductEditorActivity.EXTRA_SOURCE, page.sourceSha256)
                                    putExtra(ProductEditorActivity.EXTRA_ID, id)
                                })
                            }
                            true
                        }
                        setOnClickListener {
                            if (!id.isNullOrBlank()) {
                                startActivity(Intent(this@ProductsActivity, ProductDetailsActivity::class.java).apply {
                                    putExtra(ProductDetailsActivity.EXTRA_RUN_ID, runId)
                                    putExtra(ProductDetailsActivity.EXTRA_PRODUCT_ID, id)
                                })
                            }
                        }
                    }, LinearLayout.LayoutParams(-1, 48))
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
