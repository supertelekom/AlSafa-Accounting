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
                val candidate=if(part.isEmpty())word else part+" "+word
                if(paint.measureText(candidate)>maxWidth && part.isNotEmpty()) {
                    result.add(part)
                    part=word
                } else part=candidate
            }
            if(part.isNotEmpty()) result.add(part)
            return result.ifEmpty { listOf("") }
        }
        // Match the supplied legacy receipt's two-column label/value layout.
        // Draw each row separately so the label stays on the right and the value
        // stays on the left, as on the original 80 mm print.
        val lines=mutableListOf<Pair<String,Float>>()
        fun add(value:String,size:Float=9f) { fit(value,size).forEach { lines.add(it to size) } }
        add("الصفا للساعات",13f)
        add("Alsafa Watches",10f)
        add("فاتورة مبيع",12f)
        add("رقم الفاتورة : "+id)
        add("التاريخ والوقت : "+java.text.SimpleDateFormat("yyyy/MM/dd HH:mm",java.util.Locale.US).format(java.util.Date(d.createdAt)))
        add("إسم العميل / الشركة : "+d.customer)
        d.rows.forEach { row ->
            add("نوع المنتج : "+row.name,10f)
            add("سعر المنتج الإفرادي : "+money(row.price)+" "+d.sale.currency)
            add("عدد المنتجات : "+row.qty)
        }
        // The supplied legacy PDF has a tax row with zero tax. Do not invent
        // a nonzero tax amount without an actual stored tax field.
        add("الضريبة : 0")
        add("الإجمالي : "+money(d.sale.total)+" "+d.sale.currency,11f)
        add("المقبوض : "+money(d.sale.collected)+" "+d.sale.currency)
        add("المتبقي : "+money(NativeSalesLedger.remaining(db,id))+" "+d.sale.currency,11f)
        add("طريقة الدفع : "+d.sale.channel)
        if(d.refunded>0.000001) add("المرتجعات : "+money(d.refunded)+" "+d.sale.currency)
        if(d.sale.status!="active") add("ملغاة")
        add("شكراً لثقتكم بنا",11f)
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
                val separator=value.indexOf(" : ")
                if(separator>0 && width>=190) {
                    val label=value.substring(0,separator)
                    val content=value.substring(separator+3)
                    val labelWidth=paint.measureText(label)
                    val valueWidth=paint.measureText(content)
                    if(labelWidth+valueWidth+18f<width-2*margin) {
                        canvas.drawText(label,width-margin-labelWidth,y,paint)
                        canvas.drawText(content,margin,y,paint)
                    } else canvas.drawText(value,width-margin-paint.measureText(value),y,paint)
                } else canvas.drawText(value,width-margin-paint.measureText(value),y,paint)
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
    fun share(activity:Activity,db:SQLiteDatabase,id:String,rollWidthMm:Int=80) {
        val file=createPdf(activity,db,id,rollWidthMm)
        val phone=db.rawQuery("SELECT phone FROM native_sales WHERE id=?",arrayOf(id)).use { c ->
            if(c.moveToFirst())c.getString(0) else ""
        }
        val uri=Uri.parse("content://"+activity.packageName+".invoices/"+Uri.encode(file.name))
        val digits=phone.filter { it.isDigit() }
        val number=when {
            digits.startsWith("00963") -> digits.drop(2)
            digits.startsWith("963") -> digits
            digits.startsWith("0") && digits.length==10 -> "963"+digits.drop(1)
            digits.length==9 -> "963"+digits
            else -> ""
        }
        if(number.isNotEmpty()) {
            shareAttachment(activity,uri,number)
        } else {
            shareAttachment(activity,uri,null)
        }
    }
    private fun shareAttachment(activity:Activity,uri:Uri,number:String?) {
        // WhatsApp's commonly supported Android recipient hint uses a JID.
        // This is not a public guarantee, so keep a user-visible fallback.
        val packages=listOf("com.whatsapp.w4b","com.whatsapp")
        val manager=activity.packageManager
        val available=packages.filter { pkg ->
            runCatching { manager.getPackageInfo(pkg,0);true }.getOrDefault(false)
        }
        val intent=Intent(Intent.ACTION_SEND).apply {
            type="application/pdf"
            putExtra(Intent.EXTRA_STREAM,uri)
            putExtra(Intent.EXTRA_TEXT,"فاتورة الصفا للساعات")
            if(number!=null) putExtra("jid",number+"@s.whatsapp.net")
            clipData=android.content.ClipData.newUri(activity.contentResolver,"فاتورة الصفا",uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        var launched=false
        for(pkg in available) {
            try {
                intent.setPackage(pkg)
                activity.grantUriPermission(pkg,uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                activity.startActivity(intent)
                launched=true
                break
            } catch(_:Exception) { /* Try the next installed WhatsApp package. */ }
        }
        if(!launched) {
            intent.removeExtra("jid")
            intent.setPackage(null)
            activity.startActivity(Intent.createChooser(intent,"مشاركة الفاتورة"))
        }
    }

}
