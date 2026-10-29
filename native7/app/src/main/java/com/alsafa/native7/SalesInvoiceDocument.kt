package com.alsafa.native7

import android.app.Activity
import android.content.Intent
import android.graphics.Color
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
    fun createPdf(activity:Activity,db:SQLiteDatabase,id:String):File {
        val d=detail(db,id)
        val doc=PdfDocument()
        val width=595
        val height=842
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(65,55,45);textSize=13f }
        val gold=Color.rgb(153,112,55)
        val pages=d.rows.chunked(20).ifEmpty { listOf(emptyList()) }
        try {
            pages.forEachIndexed { pageIndex,items ->
                val page=doc.startPage(PdfDocument.PageInfo.Builder(width,height,pageIndex+1).create())
                val canvas=page.canvas
                canvas.drawColor(Color.WHITE)
                fun right(value:String,y:Float,size:Float=13f,bold:Boolean=false) {
                    paint.textSize=size
                    paint.typeface=if(bold)Typeface.create("sans-serif",Typeface.BOLD) else Typeface.create("sans-serif",Typeface.NORMAL)
                    paint.color=Color.rgb(65,55,45)
                    canvas.drawText(value,width-38f-paint.measureText(value),y,paint)
                }
                paint.color=gold;paint.strokeWidth=2f
                canvas.drawLine(35f,92f,width-35f,92f,paint)
                right("الصفا للساعات",58f,25f,true)
                right("Alsafa Watches",81f,13f)
                right("فاتورة مبيع  •  "+id.take(16),126f,14f,true)
                right("التاريخ: "+java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.US).format(java.util.Date(d.createdAt)),151f)
                right("العميل: "+d.customer,176f)
                right("الهاتف: "+d.phone,201f)
                right("العملة: "+d.sale.currency,226f)
                right("الساعة / الصنف",268f,13f,true)
                var y=296f
                items.forEach { row ->
                    right(row.name.take(45),y)
                    right("${row.qty} × ${money(row.price)} = ${money(row.qty*row.price)}",y+19f,11f)
                    y+=42f
                }
                if(pageIndex==pages.lastIndex) {
                    right("إجمالي الفاتورة: ${money(d.sale.total)} ${d.sale.currency}",y+18f,15f,true)
                    right("المرتجعات: ${money(d.refunded)}",y+42f)
                    right("المقبوض: ${money(d.sale.collected)}",y+66f)
                    right("المتبقي: ${money(NativeSalesLedger.remaining(db,id))}",y+90f,14f,true)
                    right(if(d.sale.status=="active")"سارية" else "ملغاة",y+114f)
                }
                right("الصفا للساعات",810f,12f,true)
                doc.finishPage(page)
            }
            val dir=File(activity.cacheDir,"invoices").apply { mkdirs() }
            val file=File(dir,"sale-"+id.replace(Regex("[^A-Za-z0-9_-]"),"")+".pdf")
            FileOutputStream(file).use { doc.writeTo(it) }
            return file
        } finally { doc.close() }
    }
    fun print(activity:Activity,db:SQLiteDatabase,id:String) {
        val file=createPdf(activity,db,id)
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
        },PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
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
