package com.alsafa.native7

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

/** Offline PDF rendering from the committed native invoice ledger. */
internal object SalesInvoiceDocument {
    private data class Row(val name:String,val qty:Int,val price:Double)
    private data class Detail(val sale:NativeSalesLedger.Sale,val customer:String,
        val phone:String,val createdAt:Long,val rows:List<Row>,val refunded:Double)
    private fun detail(db:SQLiteDatabase,id:String):Detail {
        NativeSalesLedger.create(db)
        val sale=NativeSalesLedger.find(db,id) ?: error("الفاتورة غير موجودة")
        val header=db.rawQuery("SELECT customer,phone,created_at FROM native_sales WHERE id=?",arrayOf(id)).use { c ->
            require(c.moveToFirst());Triple(c.getString(0),c.getString(1),c.getLong(2))
        }
        val rows=mutableListOf<Row>()
        db.rawQuery("SELECT l.product_id,l.quantity,l.unit_price FROM native_sale_lines l WHERE l.sale_id=?",arrayOf(id)).use { c ->
            while(c.moveToNext()) {
                val product=LegacyProductRepository.findById(db,c.getString(0))
                rows.add(Row(product?.name ?: c.getString(0),c.getInt(1),c.getDouble(2)))
            }
        }
        val refunded=db.rawQuery("SELECT COALESCE(SUM(refund),0) FROM native_sale_returns WHERE sale_id=?",arrayOf(id)).use { c ->
            c.moveToFirst();c.getDouble(0)
        }
        return Detail(sale,header.first,header.second,header.third,rows,refunded)
    }
    private fun money(value:Double)=String.format(java.util.Locale.US,"%.2f",value)
    /** Thermal roll PDF. Width is physical millimetres; height follows invoice content. */
    fun createPdf(activity:Activity,db:SQLiteDatabase,id:String,rollWidthMm:Int=80):File {
        require(rollWidthMm in 38..110) { "عرض رول الطابعة غير مدعوم" }
        val d=detail(db,id)
        val width=(rollWidthMm*72.0/25.4).toInt()
        val margin=9f
        val lineHeight=15f
        val doc=PdfDocument()
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK;textSize=9f }
        val tajawal=runCatching { Typeface.createFromAsset(activity.assets,"Tajawal-Medium.ttf") }.getOrNull()
        val logo=runCatching { activity.assets.open("invoice-logo.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
        fun fit(value:String,size:Float):List<String> {
            paint.textSize=size
            val maxWidth=width-2*margin
            val result=mutableListOf<String>()
            var part=""
            for(word in value.split(" ")) {
                val candidate=if(part.isEmpty())word else "$"+"{part} $"+"word"
                if(paint.measureText(candidate)>maxWidth && part.isNotEmpty()) {
                    result.add(part)
                    part=word
                } else part=candidate
            }
            if(part.isNotEmpty()) result.add(part)
            return result.ifEmpty { listOf("") }
        }
        val lines=mutableListOf<Pair<String,Float>>()
        fun add(value:String,size:Float=9f) { fit(value,size).forEach { lines.add(it to size) } }
        add("الصفا للساعات",13f)
        add("Alsafa Watches",10f)
        add("فاتورة مبيع: $id")
        add("التاريخ: "+java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.US).format(java.util.Date(d.createdAt)))
        add("العميل: "+d.customer)
        add("الهاتف: "+d.phone)
        add("العملة: "+d.sale.currency)
        add("--------------------------------")
        d.rows.forEach { row ->
            add(row.name,10f)
            add("$"+"{row.qty} × $"+"{money(row.price)} = $"+"{money(row.qty*row.price)} $"+"{d.sale.currency}")
        }
        add("--------------------------------")
        add("إجمالي الفاتورة: $"+"{money(d.sale.total)} $"+"{d.sale.currency}",11f)
        add("المرتجعات: $"+"{money(d.refunded)}")
        add("المقبوض: $"+"{money(d.sale.collected)}")
        add("المتبقي: $"+"{money(NativeSalesLedger.remaining(db,id))}",11f)
        add(if(d.sale.status=="active") "سارية" else "ملغاة")
        add("الصفا للساعات")
        val logoHeight=if(logo==null)0f else 42f
        val height=(margin*2+logoHeight+lines.size*lineHeight+12f).toInt()
        try {
            val page=doc.startPage(PdfDocument.PageInfo.Builder(width,height,1).create())
            val canvas=page.canvas
            canvas.drawColor(Color.WHITE)
            var y=margin
            logo?.let {
                val logoWidth=(it.width.toFloat()/it.height*36f).coerceAtMost(width*0.65f)
                canvas.drawBitmap(it,null,RectF((width-logoWidth)/2,y,(width+logoWidth)/2,y+36f),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                y+=logoHeight
            }
            paint.typeface=tajawal ?: Typeface.create("sans-serif",Typeface.NORMAL)
            for((value,size) in lines) {
                paint.textSize=size
                paint.color=Color.BLACK
                y+=lineHeight
                canvas.drawText(value,width-margin-paint.measureText(value),y,paint)
            }
            doc.finishPage(page)
            val dir=File(activity.cacheDir,"invoices").apply { mkdirs() }
            val file=File(dir,"sale-"+id.replace(Regex("[^A-Za-z0-9_-]"),"")+"-"+rollWidthMm+"mm.pdf")
            FileOutputStream(file).use { doc.writeTo(it) }
            return file
        } finally { doc.close();logo?.recycle() }
    }
    fun print(activity:Activity,db:SQLiteDatabase,id:String,rollWidthMm:Int=80) {
        val file=createPdf(activity,db,id,rollWidthMm)
        val manager=activity.getSystemService(Activity.PRINT_SERVICE) as PrintManager
        manager.print("الصفا - فاتورة مبيع",object:PrintDocumentAdapter() {
            override fun onLayout(oldAttributes:PrintAttributes?,newAttributes:PrintAttributes?,
                cancellationSignal:CancellationSignal?,callback:LayoutResultCallback?,extras:android.os.Bundle?) {
                callback?.onLayoutFinished(PrintDocumentInfo.Builder(file.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(),true)
            }
            override fun onWrite(pages:Array<out android.print.PageRange>?,destination:ParcelFileDescriptor?,
                cancellationSignal:CancellationSignal?,callback:WriteResultCallback?) {
                try {
                    requireNotNull(destination)
                    file.inputStream().use { input ->
                        ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output -> input.copyTo(output) }
                    }
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch(e:Exception) { callback?.onWriteFailed(e.message) }
            }
        },PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.UNKNOWN_PORTRAIT)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build())
    }
    fun share(activity:Activity,db:SQLiteDatabase,id:String) {
        val file=createPdf(activity,db,id)
        val phone=db.rawQuery("SELECT phone FROM native_sales WHERE id=?",arrayOf(id)).use { c ->
            if(c.moveToFirst())c.getString(0) else ""
        }
        val uri=Uri.parse("content://"+activity.packageName+".invoices/"+Uri.encode(file.name))
        val intent=Intent(Intent.ACTION_SEND).apply {
            type="application/pdf"
            putExtra(Intent.EXTRA_STREAM,uri)
            putExtra(Intent.EXTRA_TEXT,"فاتورة الصفا للساعات")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.whatsapp.w4b")
        }
        // WhatsApp does not provide a supported API to force a document into a
        // particular recipient chat. Let the user choose the recipient in WhatsApp.
        try { activity.startActivity(intent) } catch(_:android.content.ActivityNotFoundException) {
            intent.setPackage(null)
            activity.startActivity(Intent.createChooser(intent,"مشاركة الفاتورة"))
        }
    }
}
