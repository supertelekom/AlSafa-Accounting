package com.alsafa.native8

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix

/** CODE 128 is the printed label; ML Kit is the Google barcode reader. */
object ProductBarcodeLabel {
    fun render(value: String, width: Int = 840, height: Int = 220): Bitmap {
        require(value.isNotBlank()) { "الباركود فارغ" }
        val matrix: BitMatrix = MultiFormatWriter().encode(
            value, BarcodeFormat.CODE_128, width, height,
            mapOf(EncodeHintType.MARGIN to 16)
        )
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) for (x in 0 until width) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
        return bitmap
    }

    fun renderLabel(name: String, price: String, currency: String, barcode: String): Bitmap {
        val bars = render(barcode)
        val label = Bitmap.createBitmap(900, 410, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(label)
        canvas.drawColor(Color.WHITE)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.CENTER }
        ink.textSize = 38f
        canvas.drawText(name.take(42), 450f, 55f, ink)
        canvas.drawBitmap(bars, 30f, 82f, null)
        ink.textSize = 32f
        canvas.drawText(barcode, 450f, 335f, ink)
        ink.textSize = 34f
        canvas.drawText(price + " " + currency, 450f, 388f, ink)
        return label
    }
}
