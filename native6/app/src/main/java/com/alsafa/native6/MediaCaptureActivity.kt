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
    private var confirmed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imagePath = savedInstanceState?.getString("imagePath")
        confirmed = savedInstanceState?.getBoolean("confirmed") ?: false
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
                    pendingCameraPath?.let { File(it).delete() }
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
                } catch (e: Exception) {
                    pendingCameraPath?.let { File(it).delete() }
                    pendingCameraPath=null
                    status.text = "تعذر فتح الكاميرا: ${e.message}"
                }
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
                if (selected == null || !File(selected).isFile) status.text = "اختر صورة صالحة أولاً"
                else {
                    confirmed = true
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_PATH, selected))
                    finish()
                }
            }
        })
        setContentView(layout)
        imagePath?.let { path ->
            if (File(path).isFile) {
                try { show(path) } catch (_: Exception) { imagePath = null }
            } else imagePath = null
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            // Camera captures are temporary even after confirming the final JPEG.
            pendingCameraPath?.let { File(it).delete() }
            if (!confirmed) imagePath?.let { path ->
                runCatching {
                    val file = File(path).canonicalFile
                    val directory = File(filesDir, "images").canonicalFile
                    if (file.parentFile == directory &&
                        file.name.matches(Regex("[a-fA-F0-9-]{36}\\.jpg"))) file.delete()
                }
            }
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("confirmed", confirmed)
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
                    require(bounds.outWidth>0 && bounds.outHeight>0) { "ملف الصورة تالف" }
                    while(maxOf(bounds.outWidth,bounds.outHeight)/sample>2400)sample*=2
                    BitmapFactory.decodeFile(source.absolutePath,BitmapFactory.Options().apply { inSampleSize=sample })
                        ?: error("تعذر قراءة صورة الكاميرا")
                }
                GALLERY -> {
                    val uri = data?.data ?: error("لم يتم اختيار صورة")
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri)?.use { boundsStream ->
                        BitmapFactory.decodeStream(boundsStream, null, bounds)
                    } ?: error("تعذر فتح الصورة المختارة")
                    require(bounds.outWidth>0 && bounds.outHeight>0) { "الصورة المختارة غير صالحة" }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
                    contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                            inSampleSize = sample
                        })
                    } ?: error("تعذر قراءة الصورة")
                }
                else -> return
            }
            // Normalize the orientation encoded by the camera or gallery provider.
            // Some Android cameras save JPEG pixels sideways and rely on EXIF.
            val orientation = when (requestCode) {
                CAMERA -> pendingCameraPath?.let { readPhotoRotation(File(it)) } ?: 0
                GALLERY -> data?.data?.let { uri ->
                    contentResolver.openInputStream(uri)?.use { stream ->
                        androidx.exifinterface.media.ExifInterface(stream).rotationDegrees
                    }
                } ?: 0
                else -> 0
            }
            val uprightBitmap = if (orientation != 0) {
                val matrix = android.graphics.Matrix().apply { postRotate(orientation.toFloat()) }
                Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
            } else bitmap
            if (uprightBitmap !== bitmap) bitmap.recycle()
            val maxSide = maxOf(uprightBitmap.width, uprightBitmap.height)
            val outputBitmap = if (maxSide > 1600) {
                val scale = 1600.0 / maxSide
                Bitmap.createScaledBitmap(uprightBitmap, (uprightBitmap.width * scale).toInt().coerceAtLeast(1), (uprightBitmap.height * scale).toInt().coerceAtLeast(1), true)
            } else uprightBitmap
            // The previous selection belongs to this picker only. Never remove a photo
            // already referenced by a product or repair while replacing the preview.
            val previousSelection = imagePath
            val directory = File(filesDir, "images").apply {
                require(isDirectory || mkdirs()) { "تعذر إنشاء مجلد الصور المحلي" }
                require(isDirectory && canonicalFile.parentFile == filesDir.canonicalFile) {
                    "مسار حفظ الصور غير آمن"
                }
            }
            val output = File(directory, "${UUID.randomUUID()}.jpg")
            try {
                FileOutputStream(output).use { stream ->
                    require(outputBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)) { "تعذر حفظ الصورة" }
                    stream.flush()
                    stream.fd.sync()
                }
                require(output.length() in 1L..(12L*1024*1024)) { "حجم الصورة المحفوظة غير مدعوم" }
            } catch (e: Exception) {
                output.delete()
                throw e
            }
            if(outputBitmap !== uprightBitmap) uprightBitmap.recycle()
            outputBitmap.recycle()
            pendingCameraPath?.let { File(it).delete() }
            pendingCameraPath=null
            previousSelection?.takeIf { it != output.absolutePath }?.let { old ->
                runCatching {
                    val oldFile=File(old).canonicalFile
                    if(oldFile.parentFile==directory.canonicalFile &&
                        oldFile.name.matches(Regex("[a-fA-F0-9-]{36}\\.jpg"))) oldFile.delete()
                }
            }
            imagePath = output.absolutePath
            show(output.absolutePath)
        } catch (e: Exception) {
            if(requestCode==CAMERA) { pendingCameraPath?.let { File(it).delete() }; pendingCameraPath=null }
            status.text = "تعذر حفظ الصورة: ${e.message}"
        }
    }

    private fun readPhotoRotation(file: File): Int =
        androidx.exifinterface.media.ExifInterface(file.absolutePath).rotationDegrees

    private fun show(path: String) {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(path,bounds)
        var sample=1
        require(bounds.outWidth>0 && bounds.outHeight>0) { "ملف الصورة غير قابل للعرض" }
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1200)sample*=2
        val thumbnail=BitmapFactory.decodeFile(path,BitmapFactory.Options().apply { inSampleSize=sample })
            ?: error("تعذر عرض معاينة الصورة")
        preview.setImageBitmap(thumbnail)
        status.text = "الصورة محفوظة محلياً، اضغط استخدام الصورة للمتابعة"
    }
}
