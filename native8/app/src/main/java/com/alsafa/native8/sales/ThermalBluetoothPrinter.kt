package com.alsafa.native8.sales

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.IOException
import java.util.UUID

/** ESC/POS text printing over paired Bluetooth SPP. Never writes accounting records. */
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
    fun print(address: String, receipt: String, widthMm: Int) {
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
                out.write(receipt.toByteArray(Charsets.UTF_8))
                out.write(byteArrayOf(0x0A, 0x0A, 0x0A))
                out.flush()
            }
        } finally {
            try { socket.close() } catch (_: IOException) {}
        }
    }
}
