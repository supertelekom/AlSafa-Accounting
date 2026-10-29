package com.alsafa.native7

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Classic Bluetooth SPP ESC/POS raster printing for compatible thermal printers.
 * Not every Bluetooth printer implements ESC/POS. No network connection is used.
 */
internal object ThermalBluetoothPrinter {
    private val spp=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    fun paired(activity:Activity):List<Pair<String,String>> {
        val adapter=(activity.getSystemService(Activity.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            ?: error("البلوتوث غير متوفر")
        if(Build.VERSION.SDK_INT>=31 && activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
            error("اسمح للتطبيق بالاتصال بالبلوتوث من إعدادات الأذونات")
        return adapter.bondedDevices.map { (it.name ?: "طابعة") to it.address }
    }
    fun print(activity:Activity,file:File,address:String,rollWidthMm:Int,onDone:(String?)->Unit) {
        require(rollWidthMm in 38..110)
        if(Build.VERSION.SDK_INT>=31 && activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
            error("يلزم إذن الاتصال بالبلوتوث")
        val adapter=(activity.getSystemService(Activity.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            ?: error("البلوتوث غير متوفر")
        thread(name="alsafa-escpos") {
            var socket:BluetoothSocket?=null
            try {
                val device=adapter.getRemoteDevice(address)
                socket=device.createRfcommSocketToServiceRecord(spp)
                socket.connect()
                val out=socket.outputStream
                out.write(byteArrayOf(27,64))
                val fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    PdfRenderer(fd).use { renderer ->
                        val page=renderer.openPage(0)
                        try {
                            // Typical 203 dpi thermal heads. Keep raster width
                            // aligned to 8 dots for ESC/POS GS v 0.
                            val dots=((rollWidthMm*8).coerceAtMost(832)/8)*8
                            val height=(page.height.toDouble()*dots/page.width).toInt().coerceAtLeast(1)
                            val bitmap=Bitmap.createBitmap(dots,height,Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                var y=0
                                while(y<height) {
                                    val rows=minOf(128,height-y)
                                    val bytesPerRow=dots/8
                                    val payload=ByteArray(rows*bytesPerRow)
                                    for(yy in 0 until rows) for(x in 0 until dots) {
                                        val pixel=bitmap.getPixel(x,y+yy)
                                        val darkness=(Color.red(pixel)*299+Color.green(pixel)*587+Color.blue(pixel)*114)/1000
                                        if(darkness<150) {
                                            val index=yy*bytesPerRow+x/8
                                            payload[index]=(payload[index].toInt() or (0x80 shr (x%8))).toByte()
                                        }
                                    }
                                    out.write(byteArrayOf(29,118,48,0,
                                        (bytesPerRow and 255).toByte(),(bytesPerRow shr 8).toByte(),
                                        (rows and 255).toByte(),(rows shr 8).toByte()))
                                    out.write(payload)
                                    y+=rows
                                }
                            } finally { bitmap.recycle() }
                        } finally { page.close() }
                    }
                } finally { fd.close() }
                out.write(byteArrayOf(10,10,10))
                out.flush()
                activity.runOnUiThread { onDone(null) }
            } catch(e:Exception) {
                activity.runOnUiThread { onDone(e.message ?: "تعذرت الطباعة") }
            } finally { runCatching { socket?.close() } }
        }
    }
}
