package com.alsafa.native8

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.util.Base64
import android.graphics.BitmapFactory
import android.widget.ImageView
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.alsafa.native8.data.operational.OperationalDatabase
import com.alsafa.native8.data.operational.ProductEditRepository
import org.json.JSONObject

/** Initial non-financial editor; original stock and funding are deliberately read-only. */
class ProductEditorActivity : Activity() {
    companion object {
        const val EXTRA_SOURCE = "product_source"
        const val EXTRA_ID = "product_id"
    }

    private var photo: String? = null
    private var preview: ImageView? = null
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 8101 || resultCode != RESULT_OK) return
        try {
            val uri = data?.data ?: return
            val mime = contentResolver.getType(uri) ?: ""
            require(mime in listOf("image/jpeg", "image/png", "image/webp")) { "صيغة الصورة غير مدعومة" }
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("تعذر قراءة الصورة")
            require(bytes.size <= 3000000) { "الصورة أكبر من 3 ميغابايت" }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("صورة غير صالحة")
            photo = "data:" + mime + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            preview?.setImageBitmap(bitmap)
        } catch (e: Exception) {
            AlertDialog.Builder(this).setMessage(e.message ?: "تعذر قراءة الصورة").setPositiveButton("إغلاق", null).show()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent.getStringExtra(EXTRA_SOURCE)
        val id = intent.getStringExtra(EXTRA_ID)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = if (id == null) "إضافة منتج" else "تعديل بيانات المنتج"
            textSize = 23f
        })
        val fields = linkedMapOf<String, EditText>()
        val labels = listOf(
            "name" to "اسم المنتج",
            "brand" to "الماركة",
            "model" to "الموديل",
            "description" to "الوصف",
            "barcode" to "الباركود",
            "category" to "التصنيف",
            "salePrice" to "سعر البيع",
            "cost" to "رأس المال",
            "stock" to "الكمية",
            "currency" to "العملة"
        )
        for ((key, label) in labels) {
            root.addView(TextView(this).apply { text = label })
            val edit = EditText(this).apply {
                hint = label
                setSingleLine(key != "description")
            }
            if (key == "stock" && id != null && !id.startsWith("local-")) {
                edit.isEnabled = false
                edit.hint = "تُعدّل الكمية من حركات المخزون"
            }
            fields[key] = edit
            root.addView(edit)
        }
        root.addView(Button(this).apply {
            text = "الصورة الرئيسية من الاستديو"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }, 8101)
            }
        })
        preview = ImageView(this).apply { adjustViewBounds = true }
        root.addView(preview, LinearLayout.LayoutParams(-1, 280))
        val status = TextView(this).apply {
            text = "الكمية للمنتج المستورد للعرض فقط حتى ربط حركات المخزون؛ لا تُغيّر الفواتير السابقة."
        }
        root.addView(status)
        root.addView(Button(this).apply {
            tag = "save"
            text = "حفظ"
            setOnClickListener {
                isEnabled = false
                val button = this
                val changed = JSONObject()
                for ((key, field) in fields) {
                    if (field.isEnabled) changed.put(key, field.text.toString().trim())
                }
                photo?.let { changed.put("photo", it) }
                Thread {
                    val outcome = try {
                        val db = OperationalDatabase(applicationContext)
                        try {
                            val repository = ProductEditRepository(db)
                            if (id == null) {
                                repository.createLocal(changed)
                            } else if (id.startsWith("local-")) {
                                repository.updateLocal(id, changed)
                            } else {
                                require(!source.isNullOrBlank()) { "Missing product source" }
                                repository.saveEdit(source, id, changed)
                            }
                            null
                        } finally { db.close() }
                    } catch (e: Exception) { e.message ?: "تعذر الحفظ" }
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        button.isEnabled = true
                        if (outcome == null) {
                            AlertDialog.Builder(this@ProductEditorActivity)
                                .setMessage("تم حفظ بيانات المنتج في SQLite التشغيلية")
                                .setPositiveButton("إغلاق") { _, _ -> finish() }
                                .show()
                        } else status.text = "فشل الحفظ: " + outcome
                    }
                }.start()
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
        if (id != null) {
            Thread {
                val existing = try {
                    val db = OperationalDatabase(applicationContext)
                    try {
                        val repo = ProductEditRepository(db)
                        if (id.startsWith("local-")) repo.localById(id)
                        else if (!source.isNullOrBlank()) repo.resolved(source, id)
                        else null
                    } finally { db.close() }
                } catch (_: Exception) { null }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (existing == null) {
                        status.text = "تعذر قراءة المنتج؛ لا تحفظ تعديلات قبل التأكد من بياناته."
                        root.findViewWithTag<Button>("save")?.isEnabled = false
                    } else {
                        for ((key, field) in fields) field.setText(existing.optString(key))
                        photo = existing.optString("photo").takeIf { it.startsWith("data:image/") }
                        photo?.let {
                            try {
                                val bytes = Base64.decode(it.substringAfter(";base64,"), Base64.DEFAULT)
                                preview?.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                            } catch (_: Exception) { }
                        }
                    }
                }
            }.start()
        }
    }
}
