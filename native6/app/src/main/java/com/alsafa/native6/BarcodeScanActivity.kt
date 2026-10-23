package com.alsafa.native6

import androidx.activity.ComponentActivity
import android.content.Intent
import android.os.Bundle
import com.google.zxing.BarcodeFormat
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Real camera barcode scanner (ZXing embedded; no network or Firebase).
 * Callers receive the decoded barcode only after a successful scan.
 */
class BarcodeScanActivity : ComponentActivity() {
    companion object {
        const val EXTRA_BARCODE = "com.alsafa.native6.BARCODE"
    }

    private val scanner = registerForActivityResult(ScanContract()) { result ->
        val code = result.contents?.trim()
        if (!code.isNullOrEmpty()) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_BARCODE, code))
            finish()
        } else {
            findViewById<TextView>(1001)?.text = "لم يُقرأ باركود. حاول مرة أخرى."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
        }
        box.addView(TextView(this).apply {
            id = 1001
            text = "وجّه الكاميرا إلى باركود المنتج أو قطعة الصيانة"
            textSize = 22f
            gravity = Gravity.CENTER
        })
        box.addView(Button(this).apply {
            text = "بدء قراءة الباركود"
            setOnClickListener { launchScan() }
        })
        box.addView(Button(this).apply {
            text = "إلغاء"
            setOnClickListener { finish() }
        })
        setContentView(box)
        if (savedInstanceState == null) launchScan()
    }

    private fun launchScan() {
        scanner.launch(ScanOptions().apply {
            setDesiredBarcodeFormats(listOf(
                BarcodeFormat.EAN_13.name, BarcodeFormat.EAN_8.name,
                BarcodeFormat.UPC_A.name, BarcodeFormat.UPC_E.name,
                BarcodeFormat.CODE_128.name, BarcodeFormat.CODE_39.name,
                BarcodeFormat.ITF.name, BarcodeFormat.QR_CODE.name,
                BarcodeFormat.DATA_MATRIX.name
            ))
            setPrompt("ضع الباركود داخل الإطار")
            setBeepEnabled(true)
            setOrientationLocked(false)
        })
    }
}
