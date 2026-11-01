package com.alsafa.native8

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import com.alsafa.native8.data.migration.LegacyReadRepository
import com.alsafa.native8.data.migration.LegacyBackupStager
import com.alsafa.native8.data.migration.LegacyImportInspector
import com.alsafa.native8.data.migration.LegacyStagingDatabase

/**
 * Temporary import-verification harness. NOT the final original-source UI.
 * Financial transactions cannot be created from this screen.
 */
class MainActivity : Activity() {
    private lateinit var output: TextView
    private val pickBackup = 801
    private var productOffset = 0
    private val productPageSize = 30
    private lateinit var productsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 40, 32, 32)
        }
        layout.addView(TextView(this).apply {
            text = "الصفا Native 8 — فحص النسخة الاحتياطية"
            textSize = 22f
            gravity = Gravity.CENTER
        })
        layout.addView(Button(this).apply {
            text = "اختيار نسخة احتياطية للفحص"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "application/octet-stream"))
                }, pickBackup)
            }
        })
        productsButton = Button(this).apply {
            text = "عرض المنتجات المستوردة (فحص)"
            setOnClickListener { loadProducts() }
        }
        layout.addView(productsButton)
        output = TextView(this).apply {
            text = "الفحص يحفظ السجلات الأصلية في قاعدة استقبال منفصلة. لا ينفذ أي حركة مالية."
            textSize = 16f
        }
        layout.addView(ScrollView(this).apply { addView(output) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        layout.addView(Button(this).apply {
            text = "الصفحة التالية"
            setOnClickListener {
                productOffset += productPageSize
                loadProducts()
            }
        })
        layout.addView(Button(this).apply {
            text = "الصفحة السابقة"
            setOnClickListener {
                productOffset = (productOffset - productPageSize).coerceAtLeast(0)
                loadProducts()
            }
        })
        setContentView(layout)
    }

    private fun loadProducts() {
        output.text = "جاري قراءة المنتجات..."
        Thread {
            val message = try {
                val db = LegacyStagingDatabase(applicationContext)
                try {
                    val repository = LegacyReadRepository(db)
                    val runId = repository.latestRunId()
                    if (runId == null) {
                        "لا توجد نسخة مستوردة. اختر ملف النسخة الاحتياطية أولاً."
                    } else {
                        val page = repository.page(runId, "products", productOffset, productPageSize)
                        buildString {
                            append("المنتجات المستوردة: ").append(page.total)
                                .append(" | بدءاً من ").append(productOffset + 1).append("\\n\\n")
                            page.rows.forEach { row ->
                                val fields = row.fields
                                append("المعرف الأصلي: ").append(row.id ?: "غير موجود").append("\\n")
                                append("الاسم: ").append(fields.optString("name", "—")).append("\\n")
                                // Only display original values; never compute prices or quantities here.
                                if (fields.has("price")) append("السعر: ").append(fields.opt("price")).append("\\n")
                                if (fields.has("quantity")) append("الكمية: ").append(fields.opt("quantity")).append("\\n")
                                append("────────────\\n")
                            }
                            if (page.rows.isEmpty()) append("لا توجد سجلات في هذه الصفحة.")
                        }
                    }
                } finally { db.close() }
            } catch (e: Exception) {
                "تعذر قراءة المنتجات: " + (e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread { output.text = message }
        }.start()
    }

    @Deprecated("Used for compatibility with minSdk 26; replace with Activity Result API after parity.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickBackup || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        output.text = "جاري فحص النسخة..."
        Thread {
            val message = try {
                val db = LegacyStagingDatabase(applicationContext)
                try {
                    val result = contentResolver.openInputStream(uri)?.use {
                        LegacyBackupStager(db).stage(it)
                    } ?: error("تعذر فتح الملف")
                    val id = db.readableDatabase.rawQuery(
                        "SELECT id FROM import_runs WHERE source_sha256=?",
                        arrayOf(result.sourceSha256)
                    ).use { cursor ->
                        check(cursor.moveToFirst())
                        cursor.getLong(0)
                    }
                    val report = LegacyImportInspector(db).inspect(id)
                    buildString {
                        append("بصمة الملف: ").append(result.sourceSha256.take(16)).append("…\n")
                        append("مكرر: ").append(if (result.alreadyImported) "نعم" else "لا").append("\n")
                        append("عدد السجلات: ").append(report.sections.sumOf { it.count }).append("\n")
                        append("عدد تنبيهات العلاقات: ").append(report.issues.size).append("\n")
                        report.sections.forEach { append(it.section).append(": ").append(it.count).append("\n") }
                        report.issues.take(10).forEach { append("تنبيه: ").append(it.detail).append("\n") }
                    }
                } finally { db.close() }
            } catch (e: Exception) {
                "تعذر فحص النسخة: " + (e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread { output.text = message }
        }.start()
    }
}
