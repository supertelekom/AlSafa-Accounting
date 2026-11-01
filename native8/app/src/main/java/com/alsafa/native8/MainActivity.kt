package com.alsafa.native8

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
        output = TextView(this).apply {
            text = "الفحص يحفظ السجلات الأصلية في قاعدة استقبال منفصلة. لا ينفذ أي حركة مالية."
            textSize = 16f
        }
        layout.addView(output)
        setContentView(layout)
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
