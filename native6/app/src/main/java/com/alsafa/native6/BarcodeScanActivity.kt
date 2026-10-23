package com.alsafa.native6

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Offline, native live barcode reader. No external scanning app or cloud service. */
class BarcodeScanActivity : ComponentActivity() {
    companion object {
        const val EXTRA_BARCODE = "com.alsafa.native6.BARCODE"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val processing = AtomicBoolean(false)
    private val finished = AtomicBoolean(false)
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(
            Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_ITF, Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_DATA_MATRIX
        ).build()
    )
    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private lateinit var torchButton: Button
    private var camera: Camera? = null
    private var torchOn = false
    private var zoomed = false

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else status.text = "تحتاج قراءة الباركود إلى السماح بالكاميرا"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(16, 20, 16, 16)
        }
        status = TextView(this).apply {
            text = "وجّه الكاميرا نحو باركود الساعة"
            textSize = 21f
            gravity = Gravity.CENTER
        }
        root.addView(status)
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        torchButton = Button(this).apply {
            text = "تشغيل الإضاءة"
            isEnabled = false
            setOnClickListener {
                torchOn = !torchOn
                camera?.cameraControl?.enableTorch(torchOn)
                text = if (torchOn) "إطفاء الإضاءة" else "تشغيل الإضاءة"
            }
        }
        root.addView(torchButton)
        root.addView(Button(this).apply {
            text = "تقريب ×2"
            setOnClickListener {
                val active = camera ?: return@setOnClickListener
                val state = active.cameraInfo.zoomState.value ?: return@setOnClickListener
                val desired = if (zoomed) 1f else 2f
                val ratio = desired.coerceIn(state.minZoomRatio, state.maxZoomRatio)
                active.cameraControl.setZoomRatio(ratio)
                zoomed = !zoomed
                text = if (zoomed) "إلغاء التقريب" else "تقريب ×2"
            }
        })
        root.addView(Button(this).apply {
            text = "إلغاء"
            setOnClickListener { finish() }
        })
        setContentView(root)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else requestCamera.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                if (isFinishing || isDestroyed) return@addListener
                val provider = future.get()
                val livePreview = Preview.Builder().build().also {
                    it.surfaceProvider = preview.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { frame ->
                    if (finished.get() || !processing.compareAndSet(false, true)) {
                        frame.close()
                    } else {
                        val mediaImage = frame.image
                        if (mediaImage == null) {
                            processing.set(false)
                            frame.close()
                        } else {
                            val input = InputImage.fromMediaImage(mediaImage, frame.imageInfo.rotationDegrees)
                            scanner.process(input)
                                .addOnSuccessListener { codes ->
                                    val value = codes.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf(String::isNotEmpty) }
                                    if (value != null && finished.compareAndSet(false, true)) {
                                        setResult(RESULT_OK, Intent().putExtra(EXTRA_BARCODE, value))
                                        finish()
                                    }
                                }
                                .addOnCompleteListener {
                                    processing.set(false)
                                    frame.close()
                                }
                        }
                    }
                }
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, livePreview, analysis)
                torchButton.isEnabled = camera?.cameraInfo?.hasFlashUnit() == true
            } catch (e: Exception) {
                status.text = "تعذر تشغيل الماسح: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        scanner.close()
        executor.shutdown()
        super.onDestroy()
    }
}
