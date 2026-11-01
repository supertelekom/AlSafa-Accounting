package com.alsafa.native8

import android.graphics.Bitmap
import android.graphics.Color
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
}
