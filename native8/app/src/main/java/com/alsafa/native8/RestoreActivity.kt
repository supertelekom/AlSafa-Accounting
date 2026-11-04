package com.alsafa.native8

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.alsafa.native8.data.migration.LegacyBackupStager
import com.alsafa.native8.data.migration.LegacyImportInspector
import com.alsafa.native8.data.migration.LegacyReadRepository
import com.alsafa.native8.data.migration.LegacySupplierReconciliation
import com.alsafa.native8.data.migration.LegacyStagingDatabase
import com.alsafa.native8.sales.AppDatabase
import kotlinx.coroutines.runBlocking
import com.alsafa.native8.data.operational.RoomProductImportPromoter
import com.alsafa.native8.sales.LegacySalesArchiveImporter
import com.alsafa.native8.sales.SupplierArchiveImporter
import com.alsafa.native8.maintenance.MaintenanceLegacyImporter

/** Temporary isolated backup-import verification screen. */
class RestoreActivity : Activity() {
    companion object { const val EXTRA_PICK_ON_OPEN = "pick_backup_on_open" }
    private val requestCode = 801
    private lateinit var result: TextView
    private lateinit var productsButton: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
            setPadding(24, 24, 24, 24)
        }
        layout.addView(TextView(this).apply {
            text = "استعادة البيانات"
            textSize = 22f
            gravity = Gravity.CENTER
        })
        layout.addView(Button(this).apply {
            text = "اختيار ملف النسخة الاحتياطية"
            setOnClickListener { openBackupPicker() }
        })
        productsButton = Button(this).apply {
            text = "عرض المنتجات المستوردة"
            setOnClickListener {
                startActivity(Intent(this@RestoreActivity, ProductsActivity::class.java))
            }
        }
        layout.addView(productsButton)
        result = TextView(this).apply {
            text = "هذه مرحلة فحص الاستيراد، ولا تنفذ حركات مالية جديدة."
        }
        layout.addView(ScrollView(this).apply { addView(result) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_PICK_ON_OPEN, false)) {
            openBackupPicker()
        }
    }

    private fun openBackupPicker() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, requestCode)
        } catch (e: android.content.ActivityNotFoundException) {
            result.text = "لا يوجد تطبيق لاختيار الملفات على هذا الجهاز"
        }
    }

    @Deprecated("Legacy result callback until Activity Result API integration")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != this.requestCode || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        result.text = "جاري قراءة الملف..."
        Thread {
            val message = try {
                val db = LegacyStagingDatabase(applicationContext)
                try {
                    val staged = contentResolver.openInputStream(uri)?.use {
                        LegacyBackupStager(db).stage(it)
                    } ?: error("تعذر فتح الملف")
                    val run = db.readableDatabase.rawQuery(
                        "SELECT id FROM import_runs WHERE source_sha256=?",
                        arrayOf(staged.sourceSha256)
                    ).use { c -> check(c.moveToFirst()); c.getLong(0) }
                    val audit = try {
                        LegacyImportInspector(db).inspect(run)
                    } catch (auditError: Exception) {
                        null
                    }
                    val supplierAudit = try {
                        LegacySupplierReconciliation(LegacyReadRepository(db)).inspect(run)
                    } catch (e: Exception) { null }
                    var productError: String? = null
                    val promotion = try {
                        runBlocking {
                            RoomProductImportPromoter(db, AppDatabase.get(applicationContext)).promote(run)
                        }
                    } catch (e: Exception) { productError = e.message ?: e.javaClass.simpleName; null }
                    var archiveError: String? = null
                    val historical = try {
                        LegacySalesArchiveImporter(db, AppDatabase.get(applicationContext)).importRun(run)
                    } catch (e: Exception) { archiveError = e.message ?: e.javaClass.simpleName; null }
                    var supplierImportError: String? = null
                    val supplierArchive = try {
                        runBlocking {
                            SupplierArchiveImporter(db, AppDatabase.get(applicationContext)).importRun(run)
                        }
                    } catch (e: Exception) {
                        supplierImportError = e.message ?: e.javaClass.simpleName
                        null
                    }
                    var maintenanceError:String?=null
                    val maintenance=try {
                        runBlocking { MaintenanceLegacyImporter(db,AppDatabase.get(applicationContext)).importRun(run) }
                    } catch(e:Exception) { maintenanceError=e.message ?: e.javaClass.simpleName; null }
                    buildString {
                        append(if (staged.alreadyImported) "النسخة موجودة مسبقاً في قاعدة الفحص" else "تم حفظ النسخة في قاعدة الفحص")
                        append("\\n")
                        append("المعرف الداخلي للاستيراد: ").append(run).append("\\n")
                        append("السجلات الجديدة: ").append(staged.records).append("\\n")
                        if (audit == null) {
                            append("تعذر إكمال تدقيق العلاقات، لكن الاستيراد محفوظ.\\n")
                        } else {
                            append("إجمالي السجلات المفحوصة: ")
                                .append(audit.sections.sumOf { it.count }).append("\\n")
                            append("تنبيهات التدقيق: ").append(audit.issues.size).append("\\n")
                            audit.sections.forEach {
                                append(it.section).append(": ").append(it.count).append("\\n")
                            }
                        }
                        if (supplierAudit != null) {
                            append("تدقيق الموردين (قراءة فقط): ").append(supplierAudit.supplierCount).append(" مورد، ")
                                .append(supplierAudit.purchaseCount).append(" فاتورة شراء، ")
                                .append(supplierAudit.ledgerCount).append(" حركة دفتر\\n")
                            append("مجموعات ذات تداخل محتمل: ")
                                .append(supplierAudit.balances.count { it.possibleOverlap }).append("\\n")
                            append("تنبيهات الموردين: ").append(supplierAudit.issues.size).append("\\n")
                            append("أرصدة الموردين التاريخية لم تُرحّل مالياً.\\n")
                        } else append("تعذر إكمال تدقيق الموردين؛ لا تعتمد الأرصدة القديمة.\\n")
                        if (supplierArchive != null) {
                            append("أرشيف الموردين المنقول إلى Room: ")
                                .append(supplierArchive.suppliers).append(" مورد، ")
                                .append(supplierArchive.purchases).append(" فاتورة، ")
                                .append(supplierArchive.ledger).append(" حركة")
                                .append(if (supplierArchive.alreadyImported) " (موجود مسبقاً)" else " (نقل الآن)")
                                .append("\\n")
                        } else {
                            append("تعذر نقل أرشيف الموردين: ")
                                .append(supplierImportError ?: "خطأ غير معروف").append("\\n")
                        }
                        if (promotion == null) {
                            append("تعذر نقل المنتجات إلى Room: ").append(productError ?: "خطأ غير معروف").append("\\n")
                        } else {
                            append("المنتجات المنقولة إلى Room: ").append(promotion.count)
                            append(if (promotion.alreadyImported) " (موجودة مسبقاً)" else " (نقلت الآن)")
                            append("\\n")
                        }
                        if (historical != null) {
                            append("الفواتير التاريخية: ").append(historical.count).append("\\n")
                            append("تحتاج مراجعة: ").append(historical.review).append("\\n")
                        } else append("تعذر نقل أرشيف الفواتير: ").append(archiveError ?: "خطأ غير معروف").append("\\n")
                        if(maintenance!=null) append("الصيانة المستوردة: ").append(maintenance.repairs).append(" سجل جديد، قطع الصيانة: ").append(maintenance.parts).append(" سجل جديد\\n")
                        else append("تعذر استيراد الصيانة: ").append(maintenanceError ?: "خطأ غير معروف").append("\\n")
                        append("افتح المنتجات المستوردة للتحقق من البيانات.")
                    }
                } finally { db.close() }
            } catch (e: Exception) { "تعذر استيراد الملف: " + (e.message ?: "غير معروف") }
            runOnUiThread { result.text = message }
        }.start()
    }
}
