package com.alsafa.native6

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.RGBLuminanceSource
import org.junit.Assert.assertEquals
import org.junit.Test

/** Offline ZXing core regression: no camera or network is required for decoding. */
class BarcodeDecoderTest {
    @Test fun ean13CanBeDecodedLocally() {
        val original = "5901234123457"
        val bits = MultiFormatWriter().encode(original, BarcodeFormat.EAN_13, 500, 150)
        val pixels = IntArray(bits.width * bits.height) { i ->
            if (bits[i % bits.width, i / bits.width]) -0x1000000 else -0x1
        }
        val source = RGBLuminanceSource(bits.width, bits.height, pixels)
        val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)))
        assertEquals(original, decoded.text)
    }
}
