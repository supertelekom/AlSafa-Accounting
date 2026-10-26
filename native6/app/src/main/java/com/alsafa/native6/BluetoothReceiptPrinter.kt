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
    fun invoiceImage(file:java.io.File):Bitmap {
        android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                check(renderer.pageCount>0) { "ملف الفاتورة فارغ" }
                val heights=mutableListOf<Int>()
                for(index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        heights.add((page.height.toLong()*384/page.width).toInt().coerceAtLeast(1))
                    }
                }
                val total=heights.sum()
                require(total<=16000) { "الفاتورة طويلة جداً للطباعة دفعة واحدة" }
                val bitmap=Bitmap.createBitmap(384,total,Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                val canvas=android.graphics.Canvas(bitmap)
                var top=0
                for(index in heights.indices) {
                    renderer.openPage(index).use { page ->
                        val part=Bitmap.createBitmap(384,heights[index],Bitmap.Config.ARGB_8888)
                        try {
                            part.eraseColor(Color.WHITE)
                            page.render(part,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            canvas.drawBitmap(part,0f,top.toFloat(),null)
                        } finally { part.recycle() }
                        top+=heights[index]
                    }
                }
                return bitmap
            }
        }
    }
    /** Label preview in the printer's native 203-DPI resolution (8 dots per mm). */
    fun labelImage(name:String,price:String,barcode:String,widthMm:Int=50,heightMm:Int=30):Bitmap {
        require(widthMm in 20..82 && heightMm in 15..150) { "مقاس الملصق خارج نطاق الطابعة" }
        require(barcode.isNotBlank()) { "أضف باركودًا للمنتج أولاً" }
        val width=widthMm*8
        val height=heightMm*8
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.BLACK
            textAlign=android.graphics.Paint.Align.CENTER
        }
        val center=width/2f
        paint.typeface=android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize=minOf(21f,width/16f)
        canvas.drawText("ALSAFA",center,paint.textSize+4f,paint)
        paint.textSize=minOf(20f,width/17f)
        val maxNameChars=maxOf(10,width/12)
        canvas.drawText(name.take(maxNameChars),center,paint.textSize*2+8f,paint)
        paint.textSize=minOf(22f,width/15f)
        canvas.drawText(price,center,paint.textSize*3+13f,paint)
        val top=(paint.textSize*3+24f).toInt()
        val barcodeHeight=(height-top-27).coerceAtLeast(20)
        val matrix=com.google.zxing.MultiFormatWriter().encode(
            barcode,com.google.zxing.BarcodeFormat.CODE_128,width-24,barcodeHeight)
        paint.strokeWidth=1f
        for(y in 0 until matrix.height)for(x in 0 until matrix.width)
            if(matrix.get(x,y))canvas.drawPoint((12+x).toFloat(),(top+y).toFloat(),paint)
        paint.textSize=minOf(16f,width/22f)
        canvas.drawText(barcode.take(maxOf(10,width/11)),center,(height-6).toFloat(),paint)
        return bitmap
    }
}
