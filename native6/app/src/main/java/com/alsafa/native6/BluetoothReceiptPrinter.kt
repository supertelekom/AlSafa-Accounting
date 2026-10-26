package com.alsafa.native6

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import java.util.UUID

object BluetoothReceiptPrinter {
    fun permitted(context:Context)=Build.VERSION.SDK_INT<31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED
    @Suppress("DEPRECATION")
    fun paired(context:Context):List<android.bluetooth.BluetoothDevice> {
        check(permitted(context)) { "يجب السماح بإذن البلوتوث" }
        val adapter=BluetoothAdapter.getDefaultAdapter() ?: error("البلوتوث غير متاح")
        check(adapter.isEnabled) { "يرجى تشغيل البلوتوث" }
        return adapter.bondedDevices.sortedBy { it.name.orEmpty() }
    }
    @Suppress("DEPRECATION")
    fun print(context:Context,address:String,image:Bitmap) {
        check(permitted(context)) { "يجب السماح بإذن البلوتوث" }
        val adapter=BluetoothAdapter.getDefaultAdapter() ?: error("البلوتوث غير متاح")
        val device=adapter.bondedDevices.firstOrNull { it.address==address }
            ?: error("الطابعة غير مقترنة")
        val socket=device.createRfcommSocketToServiceRecord(
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
        try {
            socket.connect()
            val out=socket.outputStream
            out.write(byteArrayOf(27,64))
            val bitmap=Bitmap.createScaledBitmap(image,384,
                (image.height.toLong()*384/image.width).toInt().coerceAtLeast(1),true)
            try {
                var top=0
                val pixels=IntArray(384*96)
                while(top<bitmap.height) {
                    val h=minOf(96,bitmap.height-top)
                    bitmap.getPixels(pixels,0,384,0,top,384,h)
                    out.write(byteArrayOf(29,118,48,0,48,0,h.toByte(),0))
                    val data=ByteArray(48*h)
                    for(y in 0 until h)for(x in 0 until 384) {
                        val color=pixels[y*384+x]
                        val light=(Color.red(color)*299+Color.green(color)*587+Color.blue(color)*114)/1000
                        if(Color.alpha(color)>80 && light<155) {
                            val index=y*48+x/8
                            data[index]=(data[index].toInt() or (128 shr (x%8))).toByte()
                        }
                    }
                    out.write(data)
                    top+=h
                }
                out.write(byteArrayOf(27,100,3))
                out.flush()
            } finally { if(bitmap!==image)bitmap.recycle() }
        } finally { runCatching { socket.close() } }
    }
}
