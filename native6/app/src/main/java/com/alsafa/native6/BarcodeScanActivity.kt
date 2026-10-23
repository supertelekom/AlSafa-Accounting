package com.alsafa.native6

import android.app.Activity
import androidx.activity.ComponentActivity
import android.content.Intent
import android.os.Bundle
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
            setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            setPrompt("ضع الباركود داخل الإطار")
            setBeepEnabled(false)
            setOrientationLocked(false)
        })
    }
}
