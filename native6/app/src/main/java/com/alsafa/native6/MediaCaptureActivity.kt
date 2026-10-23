package com.alsafa.native6

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.content.FileProvider
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Native camera/gallery capture. Images are stored privately and returned as local
 * file paths; this screen does not modify any product or repair record on its own.
 */
class MediaCaptureActivity : Activity() {
    companion object {
        const val EXTRA_RESULT_PATH = "com.alsafa.native6.IMAGE_PATH"
        private const val CAMERA = 41
        private const val GALLERY = 42
    }
    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private var imagePath: String? = null
    private var pendingCameraPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imagePath = savedInstanceState?.getString("imagePath")
        pendingCameraPath = savedInstanceState?.getString("pendingCameraPath")
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 24, 24, 24)
        }
        status = TextView(this).apply { text = "تصوير المنتج أو الصيانة"; textSize = 21f }
        layout.addView(status)
        preview = ImageView(this).apply { adjustViewBounds = true }
        layout.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(Button(this).apply {
            text = "فتح الكاميرا"
            setOnClickListener {
                try {
                    val dir=File(cacheDir,"camera").apply { mkdirs() }
                    val output=File(dir,"${UUID.randomUUID()}.jpg")
                    pendingCameraPath=output.absolutePath
                    val uri=FileProvider.getUriForFile(this,packageName+".share",output)
                    val intent=Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_OUTPUT,uri)
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData=android.content.ClipData.newUri(contentResolver,"AlSafa camera",uri)
                    }
                    startActivityForResult(intent,CAMERA)
                } catch (e: Exception) { status.text = "تعذر فتح الكاميرا: ${e.message}" }
            }
        })
        layout.addView(Button(this).apply {
            text = "اختيار صورة من المعرض"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }, GALLERY)
            }
        })
        layout.addView(Button(this).apply {
            text = "استخدام الصورة"
            setOnClickListener {
                val selected = imagePath
                if (selected == null) status.text = "اختر صورة أولاً"
                else {
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_PATH, selected))
                    finish()
                }
            }
        })
        setContentView(layout)
        imagePath?.let { show(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("imagePath", imagePath)
        outState.putString("pendingCameraPath", pendingCameraPath)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            if(requestCode==CAMERA) {
                pendingCameraPath?.let { File(it).delete() }
                pendingCameraPath=null
            }
            return
        }
        try {
            val bitmap: Bitmap = when (requestCode) {
                CAMERA -> {
                    val source=File(pendingCameraPath ?: error("مسار الكاميرا غير متوفر"))
                    require(source.isFile && source.length()>0) { "لم تحفظ الكاميرا الصورة" }
                    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    BitmapFactory.decodeFile(source.absolutePath,bounds)
                    var sample=1
                    while(maxOf(bounds.outWidth,bounds.outHeight)/(sample*2)>=1600)sample*=2
                    BitmapFactory.decodeFile(source.absolutePath,BitmapFactory.Options().apply { inSampleSize=sample })
                        ?: error("تعذر قراءة صورة الكاميرا")
                }
                GALLERY -> {
                    val uri = data?.data ?: error("لم يتم اختيار صورة")
                    contentResolver.openInputStream(uri).use { stream ->
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        contentResolver.openInputStream(uri).use { boundsStream ->
                            BitmapFactory.decodeStream(boundsStream, null, bounds)
                        }
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) {
                            sample *= 2
                        }
                        BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                            inSampleSize = sample
                        }) ?: error("تعذر قراءة الصورة")
                    }
                }
                else -> return
            }
            val maxSide = maxOf(bitmap.width, bitmap.height)
            val outputBitmap = if (maxSide > 1600) {
                val scale = 1600.0 / maxSide
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
            } else bitmap
            val directory = File(filesDir, "images").apply { mkdirs() }
            val output = File(directory, "${UUID.randomUUID()}.jpg")
            FileOutputStream(output).use { stream ->
                require(outputBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)) { "تعذر حفظ الصورة" }
            }
            pendingCameraPath?.let { File(it).delete() }
            pendingCameraPath=null
            imagePath = output.absolutePath
            show(output.absolutePath)
        } catch (e: Exception) {
            status.text = "تعذر حفظ الصورة: ${e.message}"
        }
    }

    private fun show(path: String) {
        preview.setImageBitmap(BitmapFactory.decodeFile(path))
        status.text = "الصورة محفوظة محلياً، اضغط استخدام الصورة للمتابعة"
    }
}
