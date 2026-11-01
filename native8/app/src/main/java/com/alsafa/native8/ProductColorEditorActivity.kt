package com.alsafa.native8

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.util.Base64
import android.widget.ImageView
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.alsafa.native8.data.operational.OperationalDatabase
import com.alsafa.native8.data.operational.ProductVariantRepository
import org.json.JSONObject

class ProductColorEditorActivity : Activity() {
    private var photoData: String? = null
    private var cameraUri: Uri? = null
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        cameraUri?.let { outState.putString("color_camera_uri", it.toString()) }
    }
    private var photoPreview: ImageView? = null
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 9101 && requestCode != 9102) return
        val fromCamera = requestCode == 9102
        val uri = if (fromCamera) cameraUri else data?.data
        if (resultCode != RESULT_OK || uri == null) {
            if (fromCamera) clearCameraImage()
            return
        }
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 12_000_000) { "الصورة كبيرة جداً" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("تعذر قراءة الصورة")
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            require(options.outWidth > 0 && options.outHeight > 0) { "صورة غير صالحة" }
            var sample = 1
            while (options.outWidth / sample > 1200 || options.outHeight / sample > 1200) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("تعذر فك الصورة")
            val compressed = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, compressed)
            require(compressed.size() <= 3_000_000) { "الصورة كبيرة جداً" }
            photoData = "data:image/jpeg;base64," + Base64.encodeToString(compressed.toByteArray(), Base64.NO_WRAP)
            photoPreview?.setImageBitmap(bitmap)
        } catch (e: Exception) {
            AlertDialog.Builder(this).setMessage(e.message ?: "تعذر تحميل الصورة").setPositiveButton("إغلاق", null).show()
        } finally {
            if (fromCamera) clearCameraImage()
        }
    }
    private fun clearCameraImage() {
        val pending = cameraUri
        cameraUri = null
        if (pending != null) try { contentResolver.delete(pending, null, null) } catch (_: Exception) { }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraUri = savedInstanceState?.getString("color_camera_uri")?.let { Uri.parse(it) }
        val source = intent.getStringExtra("source").orEmpty()
        val parent = intent.getStringExtra("parent").orEmpty()
        val variantId = intent.getStringExtra("variant")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = if (variantId == null) "إضافة لون للمنتج" else "تعديل لون المنتج"
            textSize = 23f
        })
        val inputs = linkedMapOf<String, EditText>()
        for ((key, title) in listOf("color" to "اسم اللون", "stock" to "الكمية",
            "salePrice" to "سعر البيع", "cost" to "رأس المال",
            "barcode" to "الباركود")) {
            root.addView(TextView(this).apply { text = title })
            val input = EditText(this).apply { hint = title; setSingleLine(true) }
            inputs[key] = input
            root.addView(input)
        }
        root.addView(Button(this).apply {
            text = "اختيار صورة اللون من الاستديو"
            setOnClickListener { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }, 9101) }
        })
        root.addView(Button(this).apply {
            text = "التقاط صورة اللون بالكاميرا"
            setOnClickListener {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "alsafa_color_" + System.currentTimeMillis() + ".jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
                cameraUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (cameraUri != null) startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, 9102)
            }
        })
        photoPreview = ImageView(this).apply { adjustViewBounds = true }
        root.addView(photoPreview, LinearLayout.LayoutParams(-1, 220))
        root.addView(Button(this).apply {
            text = "حذف صورة اللون"
            setOnClickListener { photoData = ""; photoPreview?.setImageDrawable(null) }
        })
        val status = TextView(this)
        root.addView(status)
        val save = Button(this).apply {
            text = "حفظ اللون"
            isEnabled = variantId == null
            setOnClickListener {
                isEnabled = false
                val data = JSONObject()
                inputs.forEach { (key, field) -> data.put(key, field.text.toString().trim()) }
                photoData?.let { data.put("photo", it) }
                Thread {
                    val error = try {
                        val db = OperationalDatabase(applicationContext)
                        try { ProductVariantRepository(db).save(source, parent, data, variantId) }
                        finally { db.close() }
                        null
                    } catch (e: Exception) { e.message ?: "تعذر حفظ اللون" }
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        isEnabled = true
                        if (error == null) finish() else status.text = error
                    }
                }.start()
            }
        }
        root.addView(save)
        if (variantId != null) {
            root.addView(Button(this).apply {
                text = "حذف اللون"
                setOnClickListener {
                    AlertDialog.Builder(this@ProductColorEditorActivity)
                        .setMessage("حذف هذا اللون؟")
                        .setNegativeButton("إلغاء", null)
                        .setPositiveButton("حذف") { _, _ ->
                            Thread {
                                val db = OperationalDatabase(applicationContext)
                                try { ProductVariantRepository(db).delete(source, parent, variantId) }
                                finally { db.close() }
                                runOnUiThread { finish() }
                            }.start()
                        }.show()
                }
            })
            Thread {
                val db = OperationalDatabase(applicationContext)
                val color = try { ProductVariantRepository(db).get(variantId) } finally { db.close() }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (color == null) status.text = "تعذر تحميل اللون"
                    else {
                        inputs.forEach { (key, field) -> field.setText(color.optString(key)) }
                        photoData = color.optString("photo")
                        try {
                            val encoded = photoData.orEmpty()
                            if (encoded.startsWith("data:image/")) {
                                val bytes = Base64.decode(encoded.substringAfter(";base64,"), Base64.DEFAULT)
                                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                                var sample = 1
                                while (bounds.outWidth / sample > 600 || bounds.outHeight / sample > 600) sample *= 2
                                photoPreview?.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }))
                            }
                        } catch (_: Exception) { }
                        save.isEnabled = true
                    }
                }
            }.start()
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
