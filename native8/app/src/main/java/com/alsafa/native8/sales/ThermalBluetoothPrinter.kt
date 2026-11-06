package com.alsafa.native8.sales

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.IOException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.graphics.Typeface
import java.util.UUID

/** ESC/POS raster printing over paired Bluetooth SPP. Never writes accounting records. */
class ThermalBluetoothPrinter(private val context: Context) {
    data class PairedPrinter(val name: String, val address: String)
    private fun adapter(): BluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            ?: error("البلوتوث غير متاح")
    private fun ensurePermission() {
        if (Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            throw SecurityException("يجب منح إذن الاتصال بالبلوتوث")
    }
    fun paired(): List<PairedPrinter> {
        ensurePermission()
        return adapter().bondedDevices.map { PairedPrinter(it.name ?: "طابعة", it.address) }
            .sortedBy { it.name }
    }
    private fun rasterizeArabic(receipt: String, width: Int): Bitmap {
        val padding = 12
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = if (width <= 384) 24f else 30f
            typeface = Typeface.createFromAsset(context.assets,"fonts/Tajawal-Medium.ttf")
        }
        val layout = StaticLayout.Builder.obtain(receipt, 0, receipt.length, paint, width - 2 * padding)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .setIncludePad(true)
            .build()
        require(layout.height < 30000) { "الفاتورة طويلة جداً للطباعة" }
        val bitmap = Bitmap.createBitmap(width, layout.height + padding * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.translate(padding.toFloat(), padding.toFloat())
        layout.draw(canvas)
        return bitmap
    }

    fun print(address:String,receipt:String,widthMm:Int)=send(address,widthMm,sequenceOf(rasterizeArabic(receipt,if(widthMm==58)384 else 576)))
    fun printReceipt(address:String,receipt:InvoiceReceipt,widthMm:Int)=send(address,widthMm,receipt.bitmaps(context,if(widthMm==58)384 else 576))
    private fun send(address:String,widthMm:Int,bitmaps:Sequence<Bitmap>) {
        require(widthMm in setOf(58, 80)) { "عرض الرول غير مدعوم حالياً" }
        ensurePermission()
        val bluetooth = adapter()
        require(bluetooth.isEnabled) { "يرجى تشغيل البلوتوث" }
        val device = bluetooth.bondedDevices.firstOrNull { it.address == address }
            ?: error("يجب إقران الطابعة من إعدادات البلوتوث أولاً")
        val socket = device.createRfcommSocketToServiceRecord(
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
        try {
            socket.connect()
            socket.outputStream.use { out ->
                out.write(byteArrayOf(0x1B, 0x40)) // ESC @ initialize
                for(bitmap in bitmaps) { try {
                val bytesPerRow = (bitmap.width + 7) / 8
                val chunkHeight = 128
                var start = 0
                while (start < bitmap.height) {
                    val height = minOf(chunkHeight, bitmap.height - start)
                    out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00,
                        (bytesPerRow and 0xff).toByte(), (bytesPerRow shr 8).toByte(),
                        (height and 0xff).toByte(), (height shr 8).toByte()))
                    val buffer = ByteArray(bytesPerRow * height)
                    for (y in 0 until height) for (x in 0 until bitmap.width) {
                        if (Color.red(bitmap.getPixel(x, start + y)) < 160) {
                            val index = y * bytesPerRow + x / 8
                            buffer[index] = (buffer[index].toInt() or (0x80 shr (x % 8))).toByte()
                        }
                    }
                    out.write(buffer)
                    start += height
                }
                } finally {bitmap.recycle()} }
                out.write(byteArrayOf(0x0A, 0x0A, 0x0A))
                out.flush()
            }
        } finally {
            try { socket.close() } catch (_: IOException) {}
        }
    }
}
