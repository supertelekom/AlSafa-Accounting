package com.alsafa.native6

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

/** Native reproduction of the 80mm text receipt layout from legacy assets/main.js. */
class InvoicePdf(private val context:Context,private val store:Store) {
    private val black=Color.rgb(22,22,22)
    private val gray=Color.rgb(175,175,175)
    private val pageWidth=320 // 80mm at 4 PDF points/mm
    private val left=24f
    private val right=296f
    private val font:Typeface by lazy {
        runCatching { Typeface.createFromAsset(context.assets,"Tajawal-Medium.ttf") }
            .getOrElse { Typeface.DEFAULT }
    }
    private fun money(amount:Long,currency:Currency)=Money(amount,currency).display()
    fun write(id:String,output:OutputStream) {
        val invoice=store.invoice(id)
        val items=store.rows("SELECT name,qty,net FROM invoice_lines WHERE invoice_id=? ORDER BY line",id)
        val invoiceMeta=store.rows("SELECT phone,notes FROM invoices WHERE id=?",id).firstOrNull().orEmpty()
        val date=store.rows("SELECT date FROM events WHERE id=?",id).firstOrNull()?.get("date").orEmpty()
        val rows=mutableListOf<Pair<String,String>>()
        rows.add("رقم الفاتورة" to invoice.number.toString())
        rows.add("التاريخ والوقت" to date.replace("-","/"))
        rows.add("إسم العميل / الشركة" to invoice.party.ifBlank { "عميل نقدي" })
        invoiceMeta["phone"]?.takeIf { it.isNotBlank() }?.let { rows.add("رقم الهاتف" to it) }
        for(item in items) {
            rows.add("نوع المنتج" to item.getValue("name"))
            val qty=item.getValue("qty").toLongOrNull()?:1L
            val net=item.getValue("net").toLong()
            rows.add("سعر المنتج الإفرادي" to money(if(qty>0)net/qty else net,invoice.currency))
            rows.add("عدد المنتجات" to item.getValue("qty"))
        }
        // Net line totals are not original list prices; do not fabricate legacy discount/tax fields.
        rows.add("الإجمالي بعد الخصم" to money(invoice.total,invoice.currency))
        if(invoice.kind!="purchase") {
            rows.add("المقبوض الصافي" to money(store.paid(id),invoice.currency))
            rows.add("المتبقي" to money(store.due(id),invoice.currency))
            val vaults=store.rows("SELECT DISTINCT vault FROM receipts WHERE invoice_id=? ORDER BY vault",id)
                .mapNotNull { it["vault"]?.takeIf(String::isNotBlank) }
            val method=when {
                vaults.isEmpty()->"لم يُسجّل قبض"
                vaults.size>1->vaults.joinToString(" + ")
                vaults.single()=="drawer"->"نقدي"
                else->vaults.single()
            }
            rows.add("طريقة الدفع" to method)
        }
        val notes=invoiceMeta["notes"].orEmpty().trim()
        if(notes.isNotEmpty()) rows.add("ملاحظات" to notes.take(400))
        val doc=PdfDocument()
        val rowHeights=rows.map { (label,value)->
            val measure=TextPaint().apply { textSize=11f;typeface=font }
            val valueHeight=StaticLayout.Builder.obtain(value,0,value.length,measure,120)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build().height
            maxOf(29,valueHeight+13)
        }
        val pageHeight=maxOf(390,212+rowHeights.sum()+65)
        val page=doc.startPage(PdfDocument.PageInfo.Builder(pageWidth,pageHeight,1).create())
        val canvas=page.canvas
        canvas.drawColor(Color.WHITE)
        val linePaint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color=black;strokeWidth=1f
        }
        fun rule(y:Float,strong:Boolean=false) {
            linePaint.color=if(strong)black else gray
            linePaint.strokeWidth=if(strong)1.1f else .45f
            if(!strong)linePaint.pathEffect=android.graphics.DashPathEffect(floatArrayOf(2.6f,2.6f),0f)
            canvas.drawLine(left,y,right,y,linePaint)
            linePaint.pathEffect=null
        }
        fun txt(text:String,x:Float,y:Float,w:Int,size:Float,alignment:Layout.Alignment=Layout.Alignment.ALIGN_CENTER,bold:Boolean=false):Int {
            val p=TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color=black;textSize=size;typeface=if(bold)Typeface.create(font,Typeface.BOLD) else font
            }
            val layout=StaticLayout.Builder.obtain(text,0,text.length,p,w)
                .setAlignment(alignment).setIncludePad(false).build()
            canvas.save();canvas.translate(x,y);layout.draw(canvas);canvas.restore()
            return layout.height
        }
        val logo=runCatching {
            context.assets.open("invoice-logo.png").use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull()
        if(logo!=null) {
            try {
                val dst=android.graphics.RectF(136f,12f,184f,72f)
                canvas.drawBitmap(logo,null,dst,android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG))
            } finally { logo.recycle() }
        }
        txt("الصفا للساعات",left,78f,272,18f,bold=true)
        txt("Alsafa Watches",left,106f,272,10f)
        rule(128f,true)
        val title=when(invoice.kind){"repair"->"فاتورة صيانة";"purchase"->"فاتورة شراء";else->"فاتورة مبيع"}
        txt(title,left,139f,272,17f,bold=true)
        rule(170f,true)
        var y=188f
        rows.forEachIndexed { index,(label,value)->
            val h=rowHeights[index].toFloat()
            txt(label,190f,y,99,11f,Layout.Alignment.ALIGN_OPPOSITE)
            txt(":",179f,y,12,11f)
            txt(value,33f,y-2f,124,11f)
            y+=h
            rule(y-5f,label=="الإجمالي بعد الخصم")
        }
        y+=9f
        rule(y,true)
        txt("شكراً لثقتكم بنا",left,y+15f,272,14f,bold=true)
        doc.finishPage(page)
        try { doc.writeTo(output) } finally { doc.close() }
    }
}
