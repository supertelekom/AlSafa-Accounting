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
    private const val PREFS="alsafa_printer"
    private const val ADDRESS="paired_printer_address"

    /** Remember the chosen bonded printer until Bluetooth is switched off. */
    fun rememberPrinter(context:Context,address:String) {
        val matched=paired(context).any { it.address==address }
        require(matched) { "يرجى إقران الطابعة من إعدادات البلوتوث أولاً" }
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString(ADDRESS,address).apply()
    }

    fun rememberedPrinter(context:Context):String? {
        val adapter=BluetoothAdapter.getDefaultAdapter() ?: return null
        if(!adapter.isEnabled) {
            context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .remove(ADDRESS).apply()
            return null
        }
        if(!permitted(context)) return null
        val address=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getString(ADDRESS,null) ?: return null
        return if(adapter.bondedDevices.any { it.address==address }) address else null
    }

    fun forgetPrinter(context:Context) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .remove(ADDRESS).apply()
    }
    @Suppress("DEPRECATION")
    fun print(context:Context,address:String,image:Bitmap) {
        check(permitted(context)) { "يجب السماح بإذن البلوتوث" }
        val adapter=BluetoothAdapter.getDefaultAdapter() ?: error("البلوتوث غير متاح")
        val device=adapter.bondedDevices.firstOrNull { it.address==address }
            ?: error("الطابعة غير مقترنة")
        adapter.cancelDiscovery()
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
                    out.write(byteArrayOf(29,118,48,0,48,0,(h and 255).toByte(),(h shr 8).toByte()))
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
    /** Native TSPL label output: exact roll dimensions and gap, not ESC/POS receipt scaling. */
    @Suppress("DEPRECATION")
    fun printLabel(context:Context,address:String,image:Bitmap,widthMm:Int,heightMm:Int,gapMm:Int=2) {
        check(permitted(context)) { "يجب السماح بإذن البلوتوث" }
        require(widthMm in 20..82 && heightMm in 15..150 && gapMm in 0..10)
        require(image.width==widthMm*8 && image.height==heightMm*8) { "أبعاد صورة الملصق لا تطابق مقاس الرول" }
        val adapter=BluetoothAdapter.getDefaultAdapter() ?: error("البلوتوث غير متاح")
        check(adapter.isEnabled) { "البلوتوث غير مفعل" }
        val device=adapter.bondedDevices.firstOrNull { it.address==address }
            ?: error("الطابعة غير مقترنة")
        adapter.cancelDiscovery()
        val socket=device.createRfcommSocketToServiceRecord(
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
        try {
            socket.connect()
            val out=socket.outputStream
            val widthBytes=(image.width+7)/8
            val header="SIZE $widthMm mm,$heightMm mm\r\nGAP $gapMm mm,0 mm\r\nDIRECTION 1\r\nCLS\r\nBITMAP 0,0,$widthBytes,${image.height},0,"
            out.write(header.toByteArray(Charsets.US_ASCII))
            val pixels=IntArray(image.width)
            val row=ByteArray(widthBytes)
            for(y in 0 until image.height) {
                java.util.Arrays.fill(row,0)
                image.getPixels(pixels,0,image.width,0,y,image.width,1)
                for(x in pixels.indices) {
                    val c=pixels[x]
                    val lum=(Color.red(c)*299+Color.green(c)*587+Color.blue(c)*114)/1000
                    if(Color.alpha(c)>80 && lum<155)
                        row[x/8]=(row[x/8].toInt() or (128 shr (x%8))).toByte()
                }
                out.write(row)
            }
            out.write("\r\nPRINT 1,1\r\n".toByteArray(Charsets.US_ASCII))
            out.flush()
        } finally { runCatching { socket.close() } }
    }
    /** A single Bluetooth connection for all PDF pages; raster chunks bound memory use. */
    @Suppress("DEPRECATION")
    fun printInvoicePdf(context:Context,address:String,file:java.io.File) {
        check(permitted(context)) { "يجب السماح بإذن البلوتوث" }
        val adapter=BluetoothAdapter.getDefaultAdapter() ?: error("البلوتوث غير متاح")
        check(adapter.isEnabled) { "البلوتوث غير مفعل" }
        val device=adapter.bondedDevices.firstOrNull { it.address==address }
            ?: error("الطابعة غير مقترنة")
        android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            android.graphics.pdf.PdfRenderer(fd).use { renderer ->
                require(renderer.pageCount>0) { "ملف الفاتورة فارغ" }
                adapter.cancelDiscovery()
                val socket=device.createRfcommSocketToServiceRecord(
                    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                try {
                    socket.connect()
                    val out=socket.outputStream
                    out.write(byteArrayOf(27,64))
                    for(i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val height=(page.height.toLong()*384/page.width).toInt().coerceAtLeast(1)
                            var top=0
                            while(top<height) {
                                val h=minOf(96,height-top)
                                val bitmap=Bitmap.createBitmap(384,h,Bitmap.Config.ARGB_8888)
                                try {
                                    bitmap.eraseColor(Color.WHITE)
                                    val scale=384f/page.width
                                    val transform=android.graphics.Matrix().apply {
                                        setScale(scale,scale)
                                        postTranslate(0f,-top.toFloat())
                                    }
                                    page.render(bitmap,null,transform,
                                        android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                    val pixels=IntArray(384*h)
                                    bitmap.getPixels(pixels,0,384,0,0,384,h)
                                    val data=ByteArray(48*h)
                                    for(y in 0 until h) for(x in 0 until 384) {
                                        val c=pixels[y*384+x]
                                        val lum=(Color.red(c)*299+Color.green(c)*587+Color.blue(c)*114)/1000
                                        if(Color.alpha(c)>80 && lum<155) {
                                            val at=y*48+x/8
                                            data[at]=(data[at].toInt() or (128 shr (x%8))).toByte()
                                        }
                                    }
                                    out.write(byteArrayOf(29,118,48,0,48,0,
                                        (h and 255).toByte(),(h shr 8).toByte()))
                                    out.write(data)
                                } finally { bitmap.recycle() }
                                top+=h
                            }
                        }
                    }
                    out.write(byteArrayOf(27,100,3))
                    out.flush()
                } finally { runCatching { socket.close() } }
            }
        }
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
                val total=heights.sumOf { it.toLong() }
                require(total<=16000L) { "الفاتورة طويلة جداً للطباعة دفعة واحدة" }
                val bitmap=Bitmap.createBitmap(384,total.toInt(),Bitmap.Config.ARGB_8888)
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
        require(barcode.all { it.code in 32..126 }) { "الباركود يحتوي على أحرف غير مدعومة" }
        val width=widthMm*8
        val height=heightMm*8
        require(width>=160 && height>=120) { "الملصق صغير جداً للطباعة" }
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
        val available=width-24f
        val cleanName=name.trim()
        while(paint.measureText(cleanName)>available && paint.textSize>9f) paint.textSize-=1f
        var displayed=cleanName
        while(displayed.isNotEmpty() && paint.measureText(displayed)>available) displayed=displayed.dropLast(1)
        canvas.drawText(displayed,center,paint.textSize*2+8f,paint)
        paint.textSize=minOf(22f,width/15f)
        while(paint.measureText(price)>available && paint.textSize>9f) paint.textSize-=1f
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
