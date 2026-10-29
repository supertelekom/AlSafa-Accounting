package com.alsafa.native7

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.json.JSONObject
import java.io.FileOutputStream

/** One real CODE_128 label, with the product name and sale price. */
internal object BarcodeLabelPrinter {
    fun print(activity:Activity,product:JSONObject,widthMm:Int=50,heightMm:Int=30) {
        require(widthMm in 20..110 && heightMm in 15..100)
        val code=product.optString("barcode").trim()
        require(code.isNotBlank()) { "أدخل باركود المنتج أولاً" }
        val name=product.optString("name")
        val price=product.optString("salePrice")+" "+product.optString("currency","USD")
        val matrix=MultiFormatWriter().encode(code,BarcodeFormat.CODE_128,600,140)
        val bitmap=Bitmap.createBitmap(matrix.width,matrix.height,Bitmap.Config.ARGB_8888)
        for(y in 0 until matrix.height) for(x in 0 until matrix.width)
            bitmap.setPixel(x,y,if(matrix[x,y])Color.BLACK else Color.WHITE)
        val w=(widthMm*72.0/25.4).toInt().coerceAtLeast(1)
        val h=(heightMm*72.0/25.4).toInt().coerceAtLeast(1)
        val manager=activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print("Alsafa - "+name,object:PrintDocumentAdapter() {
            override fun onLayout(oldAttributes:PrintAttributes?,newAttributes:PrintAttributes?,
                cancellationSignal:CancellationSignal?,callback:LayoutResultCallback?,extras:android.os.Bundle?) {
                if(cancellationSignal?.isCanceled==true) { callback?.onLayoutCancelled();return }
                callback?.onLayoutFinished(PrintDocumentInfo.Builder("alsafa-barcode.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(),true)
            }
            override fun onWrite(pages:Array<out android.print.PageRange>?,
                destination:ParcelFileDescriptor?,cancellationSignal:CancellationSignal?,
                callback:WriteResultCallback?) {
                if(destination==null){callback?.onWriteFailed("تعذر فتح ملف الطباعة");return}
                val pdf=PdfDocument()
                try {
                    if(cancellationSignal?.isCanceled==true){callback?.onWriteCancelled();return}
                    val page=pdf.startPage(PdfDocument.PageInfo.Builder(w,h,1).create())
                    val canvas=page.canvas
                    canvas.drawColor(Color.WHITE)
                    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK;textAlign=Paint.Align.CENTER }
                    val scale=w/600f
                    canvas.drawBitmap(bitmap,null,android.graphics.RectF(4f,h*0.30f,w-4f,h*0.72f),paint)
                    paint.textSize=(w*0.075f).coerceIn(7f,14f)
                    canvas.drawText(name.take(32),w/2f,h*0.22f,paint)
                    paint.textSize=(w*0.085f).coerceIn(7f,15f)
                    canvas.drawText(price.take(28),w/2f,h*0.91f,paint)
                    pdf.finishPage(page)
                    FileOutputStream(destination.fileDescriptor).use { pdf.writeTo(it) }
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch(e:Exception) {callback?.onWriteFailed(e.message ?: "تعذرت الطباعة")}
                finally {pdf.close();bitmap.recycle()}
            }
        },PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.UNKNOWN_PORTRAIT)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build())
    }
}
