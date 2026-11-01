package com.alsafa.native8

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.util.Base64
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.provider.MediaStore
import android.content.ContentValues
import android.net.Uri
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

    private val photos = mutableMapOf<String, String>()
    private val previews = mutableMapOf<String, ImageView>()
    private val photoKeys = listOf("photo", "photo2", "photo3", "barcodePhoto")
    private var cameraUri: Uri? = null
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        cameraUri?.let { outState.putString("camera_uri", it.toString()) }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode !in 8101..8104 && requestCode !in 8201..8204) return
        val fromCamera = requestCode >= 8201
        val uri = if (fromCamera) cameraUri else data?.data
        if (resultCode != RESULT_OK || uri == null) {
            if (fromCamera) { cameraUri?.let { contentResolver.delete(it, null, null) }; cameraUri = null }
            return
        }
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    require(output.size() + n <= 12_000_000) { "حجم الصورة كبير جداً" }
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            } ?: error("تعذر قراءة الصورة")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "ملف الصورة غير صالح" }
            var sample = 1
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: error("تعذر فتح الصورة")
            val compressed = java.io.ByteArrayOutputStream()
            require(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, compressed)) { "تعذر ضغط الصورة" }
            require(compressed.size() <= 3_000_000) { "الصورة أكبر من الحد المسموح" }
            val key = photoKeys[(requestCode - if (fromCamera) 8201 else 8101)]
            photos[key] = "data:image/jpeg;base64," + Base64.encodeToString(compressed.toByteArray(), Base64.NO_WRAP)
            previews[key]?.setImageBitmap(bitmap)
        } catch (e: Exception) {
            AlertDialog.Builder(this).setMessage(e.message ?: "تعذر قراءة الصورة").setPositiveButton("إغلاق", null).show()
        } finally {
            if (fromCamera) { cameraUri?.let { contentResolver.delete(it, null, null) }; cameraUri = null }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraUri = savedInstanceState?.getString("camera_uri")?.let { Uri.parse(it) }
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
        photoKeys.forEachIndexed { index, key ->
            root.addView(Button(this).apply {
                text = listOf("الصورة الرئيسية", "الصورة الثانية", "الصورة الثالثة", "صورة الباركود")[index] + " من الاستديو"
                setOnClickListener {
                    startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }, 8101 + index)
                }
            })
            val image = ImageView(this).apply { adjustViewBounds = true }
            previews[key] = image
            root.addView(image, LinearLayout.LayoutParams(-1, 240))
        }
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
                for ((key, value) in photos) changed.put(key, value)
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
                        for (key in photoKeys) {
                            val encoded = existing.optString(key)
                            if (!encoded.startsWith("data:image/")) continue
                            photos[key] = encoded
                            try {
                                val bytes = Base64.decode(encoded.substringAfter(";base64,"), Base64.DEFAULT)
                                previews[key]?.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                            } catch (_: Exception) { }
                        }
                    }
                }
            }.start()
        }
    }
}
