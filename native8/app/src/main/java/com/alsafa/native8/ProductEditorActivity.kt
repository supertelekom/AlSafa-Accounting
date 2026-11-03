package com.alsafa.native8

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.util.Base64
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
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
import com.alsafa.native8.sales.AppDatabase
import kotlinx.coroutines.runBlocking
import com.alsafa.native8.data.operational.RoomProductVariantRepository
import com.alsafa.native8.data.operational.RoomProductEditRepository
import com.alsafa.native8.data.operational.RoomProductStockRepository
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
    private var saving = false
    private var barcodeField: EditText? = null
    private var reloadColors: (() -> Unit)? = null
    override fun onResume() {
        super.onResume()
        reloadColors?.invoke()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        cameraUri?.let { outState.putString("camera_uri", it.toString()) }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode !in 8101..8104 && requestCode !in 8201..8204 && requestCode != 8301 && requestCode != 8302) return
        val fromCamera = requestCode in 8201..8204 || requestCode == 8302
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
            if (requestCode == 8301 || requestCode == 8302) {
                val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_CODE_128, Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A).build())
                scanner.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { codes ->
                        val value = codes.firstOrNull()?.rawValue
                        if (value.isNullOrBlank()) {
                            AlertDialog.Builder(this).setMessage("لم يتم العثور على باركود واضح").setPositiveButton("إغلاق", null).show()
                        } else barcodeField?.setText(value)
                    }
                    .addOnFailureListener { e -> AlertDialog.Builder(this).setMessage(e.message ?: "تعذر قراءة الباركود").setPositiveButton("إغلاق", null).show() }
                    .addOnCompleteListener { scanner.close() }
                return
            }
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
            if (key == "stock" && id != null) {
                edit.isEnabled = false
                edit.hint = "تُعدّل الكمية من حركات المخزون"
            }
            fields[key] = edit
            if (key == "barcode") barcodeField = edit
            root.addView(edit)
        }
        root.addView(Button(this).apply {
            text = "قراءة الباركود باستخدام Google ML Kit من صورة"
            setOnClickListener { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }, 8301) }
        })
        root.addView(Button(this).apply {
            text = "مسح الباركود بالكاميرا عبر Google ML Kit"
            setOnClickListener {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "alsafa_scan_" + System.currentTimeMillis() + ".jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    }
                    cameraUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("تعذر إنشاء صورة المسح")
                    startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, 8302)
                } catch (e: Exception) {
                    cameraUri?.let { contentResolver.delete(it, null, null) }
                    cameraUri = null
                    AlertDialog.Builder(this@ProductEditorActivity).setMessage(e.message ?: "تعذر فتح الكاميرا").setPositiveButton("إغلاق", null).show()
                }
            }
        })
        root.addView(Button(this).apply {
            text = "معاينة باركود CODE 128"
            setOnClickListener {
                try {
                    val bitmap = ProductBarcodeLabel.renderLabel(
                        fields["name"]?.text?.toString().orEmpty(),
                        fields["salePrice"]?.text?.toString().orEmpty(),
                        fields["currency"]?.text?.toString().orEmpty(),
                        barcodeField?.text?.toString()?.trim().orEmpty()
                    )
                    AlertDialog.Builder(this@ProductEditorActivity).setView(ImageView(this@ProductEditorActivity).apply {
                        setImageBitmap(bitmap)
                        adjustViewBounds = true
                    }).setPositiveButton("إغلاق", null).show()
                } catch (e: Exception) { AlertDialog.Builder(this@ProductEditorActivity).setMessage(e.message ?: "تعذر إنشاء الباركود").setPositiveButton("إغلاق", null).show() }
            }
        })
        photoKeys.forEachIndexed { index, key ->
            root.addView(Button(this).apply {
                text = listOf("الصورة الرئيسية", "الصورة الثانية", "الصورة الثالثة", "صورة الباركود")[index] + " من الاستديو"
                setOnClickListener {
                    startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }, 8101 + index)
                }
            })
            root.addView(Button(this).apply {
                text = listOf("التقاط الصورة الرئيسية", "التقاط الصورة الثانية", "التقاط الصورة الثالثة", "التقاط صورة الباركود")[index] + " بالكاميرا"
                setOnClickListener {
                    try {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "alsafa_product_" + System.currentTimeMillis() + ".jpg")
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        }
                        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                            ?: error("تعذر إنشاء ملف الصورة")
                        cameraUri = uri
                        startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                            putExtra(MediaStore.EXTRA_OUTPUT, uri)
                            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, 8201 + index)
                    } catch (e: Exception) {
                        cameraUri?.let { contentResolver.delete(it, null, null) }
                        cameraUri = null
                        AlertDialog.Builder(this@ProductEditorActivity).setMessage(e.message ?: "تعذر فتح الكاميرا").setPositiveButton("إغلاق", null).show()
                    }
                }
            })
            val image = ImageView(this).apply { adjustViewBounds = true }
            previews[key] = image
            root.addView(image, LinearLayout.LayoutParams(-1, 240))
            root.addView(Button(this).apply {
                text = "حذف " + listOf("الصورة الرئيسية", "الصورة الثانية", "الصورة الثالثة", "صورة الباركود")[index]
                setOnClickListener {
                    photos[key] = ""
                    previews[key]?.setImageDrawable(null)
                }
            })
        }
        if (id != null) {
            root.addView(TextView(this).apply { text = "ألوان المنتج"; textSize = 21f })
            val colorList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            root.addView(colorList)
            val colorSource = if (id.startsWith("local-")) "local" else source.orEmpty()
            fun refreshColors() {
                Thread {
                    val result = try {
                        runBlocking { RoomProductVariantRepository(AppDatabase.get(applicationContext)).list(colorSource, id) }
                    } catch (_: Exception) { emptyList<JSONObject>() }
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        colorList.removeAllViews()
                        for (color in result) {
                            colorList.addView(Button(this@ProductEditorActivity).apply {
                                text = color.optString("color") + " | " + color.optString("stock") + " | " + color.optString("salePrice")
                                setOnClickListener {
                                    startActivity(Intent(this@ProductEditorActivity, ProductColorEditorActivity::class.java).apply {
                                        putExtra("source", colorSource)
                                        putExtra("parent", id)
                                        putExtra("variant", color.optString("id"))
                                    })
                                }
                            })
                        }
                    }
                }.start()
            }
            root.addView(Button(this).apply {
                text = "إضافة لون"
                setOnClickListener {
                    startActivity(Intent(this@ProductEditorActivity, ProductColorEditorActivity::class.java).apply {
                        putExtra("source", colorSource)
                        putExtra("parent", id)
                    })
                }
            })
            colorList.tag = "color_list"
            reloadColors = { refreshColors() }
            refreshColors()
        }
        if (id != null) root.addView(Button(this).apply {
            text = "تسوية كمية المنتج وسجل الحركات"
            setOnClickListener { startActivity(Intent(this@ProductEditorActivity, ProductStockActivity::class.java).apply {
                putExtra("source", if (id.startsWith("local-")) "local" else source.orEmpty())
                putExtra("parent", id)
            }) }
        })
        val status = TextView(this).apply {
            text = "تسوية المخزون تحفظ حركة مستقلة ولا تغيّر الفواتير أو بيانات الاستيراد الأصلية."
        }
        root.addView(status)
        val saveButton = Button(this).apply {
            tag = "save"
            text = "حفظ"
            setOnClickListener {
                if (saving) return@setOnClickListener
                saving = true
                isEnabled = false
                val button = this
                val changed = JSONObject()
                for ((key, field) in fields) {
                    if (field.isEnabled) changed.put(key, field.text.toString().trim())
                }
                for ((key, value) in photos) changed.put(key, value)
                Thread {
                    val outcome = try {
                        val repository = RoomProductEditRepository(AppDatabase.get(applicationContext))
                        runBlocking {
                            if (id == null) {
                                repository.createLocal(changed)
                            } else if (id.startsWith("local-")) {
                                repository.updateLocal(id, changed)
                            } else {
                                require(!source.isNullOrBlank()) { "Missing product source" }
                                repository.saveEdit(source, id, changed)
                            }
                            null
                        }
                    } catch (e: Exception) { e.message ?: "تعذر الحفظ" }
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (outcome == null) {
                            setResult(RESULT_OK)
                            finish()
                        } else {
                            saving = false
                            button.isEnabled = true
                            status.text = "فشل الحفظ: " + outcome
                        }
                    }
                }.start()
            }
        }
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(ScrollView(this@ProductEditorActivity).apply { addView(root) },
                LinearLayout.LayoutParams(-1, 0, 1f))
            addView(saveButton, LinearLayout.LayoutParams(-1, 72).apply {
                setMargins(24, 8, 24, 16)
            })
        }
        setContentView(screen)
        if (id != null) {
            saveButton?.isEnabled = false
            Thread {
                val existing = try {
                    val repo = RoomProductEditRepository(AppDatabase.get(applicationContext))
                    runBlocking {
                        if (id.startsWith("local-")) repo.localById(id)
                        else if (!source.isNullOrBlank()) repo.resolved(source, id)
                        else null
                    }
                } catch (_: Exception) { null }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (existing == null) {
                        status.text = "تعذر قراءة المنتج؛ لا تحفظ تعديلات قبل التأكد من بياناته."
                        root.findViewWithTag<Button>("save")?.isEnabled = false
                    } else {
                        for ((key, field) in fields) field.setText(existing.optString(key))
                        if (!id.startsWith("local-") && !source.isNullOrBlank()) {
                            Thread {
                                val stock = try {
                                    runBlocking { RoomProductStockRepository(AppDatabase.get(applicationContext)).currentStock(source, id) }
                                } catch (_: Exception) { null }
                                runOnUiThread { if (!isFinishing && !isDestroyed && stock != null) fields["stock"]?.setText(stock) }
                            }.start()
                        }
                        root.findViewWithTag<Button>("save")?.isEnabled = true
                        for (key in photoKeys) {
                            val encoded = existing.optString(key)
                            if (!encoded.startsWith("data:image/")) continue
                            photos[key] = encoded
                            try {
                                val bytes = Base64.decode(encoded.substringAfter(";base64,"), Base64.DEFAULT)
                                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                                var sample = 1
                                while (bounds.outWidth / sample > 800 || bounds.outHeight / sample > 800) sample *= 2
                                previews[key]?.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }))
                            } catch (_: Exception) { }
                        }
                    }
                }
            }.start()
        }
    }
}
