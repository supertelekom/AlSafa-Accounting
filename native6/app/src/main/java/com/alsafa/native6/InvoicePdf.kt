package com.alsafa.native6

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

/** Arabic-first AlSafa customer invoice. No cost, profit or internal accounting fields. */
class InvoicePdf(private val context:Context,private val store:Store) {
    private val ink=Color.rgb(54,47,40)
    private val gold=Color.rgb(157,115,50)
    private val muted=Color.rgb(111,102,89)
    private val light=Color.rgb(248,245,239)
    private val width=420
    private val height=595
    private val margin=25f
    private val font:Typeface by lazy {
        runCatching { Typeface.createFromAsset(context.assets,"Tajawal-Medium.ttf") }
            .getOrElse { Typeface.DEFAULT }
    }

    fun write(id:String,output:OutputStream) {
        val invoice=store.invoice(id)
        val items=store.rows("SELECT name,qty,net FROM invoice_lines WHERE invoice_id=? ORDER BY line",id)
        val date=store.rows("SELECT date FROM events WHERE id=?",id).firstOrNull()?.get("date").orEmpty()
        val document=PdfDocument()
        var pageNumber=0
        var page:PdfDocument.Page?=null
        var y=0f

        fun rule(at:Float,color:Int=Color.rgb(224,217,204)) {
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color;strokeWidth=0.8f }
            page!!.canvas.drawLine(margin,at,width-margin,at,p)
        }
        fun block(text:String,left:Float,top:Float,boxWidth:Int,size:Float=12f,
                  color:Int=ink,align:Layout.Alignment=Layout.Alignment.ALIGN_OPPOSITE,
                  bold:Boolean=false):Int {
            val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color=color;textSize=size;typeface=if(bold)Typeface.create(font,Typeface.BOLD) else font
            }
            val layout=StaticLayout.Builder.obtain(text,0,text.length,paint,boxWidth)
                .setAlignment(align).setIncludePad(false).build()
            page!!.canvas.save()
            page!!.canvas.translate(left,top)
            layout.draw(page!!.canvas)
            page!!.canvas.restore()
            return layout.height
        }
        fun nextPage() {
            page?.let { document.finishPage(it) }
            pageNumber++
            page=document.startPage(PdfDocument.PageInfo.Builder(width,height,pageNumber).create())
            page!!.canvas.drawColor(Color.WHITE)
            val stripe=Paint().apply { color=gold }
            page!!.canvas.drawRect(0f,0f,width.toFloat(),7f,stripe)
            y=22f
            val logo=runCatching {
                android.graphics.BitmapFactory.decodeResource(context.resources,R.drawable.alsafa_logo)
            }.getOrNull()
            if(logo!=null) {
                try {
                    val ratio=logo.width.toFloat()/logo.height
                    val logoHeight=48f
                    val logoWidth=minOf(90f,logoHeight*ratio)
                    val destination=android.graphics.RectF((width-logoWidth)/2f,y,(width+logoWidth)/2f,y+logoHeight)
                    page!!.canvas.drawBitmap(logo,null,destination,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                } finally { logo.recycle() }
                y+=52f
            }
            y+=block("الصفا للساعات",margin,y,(width-2*margin).toInt(),22f,gold,Layout.Alignment.ALIGN_CENTER,true)+3f
            y+=block("ALSAFA WATCHES",margin,y,(width-2*margin).toInt(),10f,muted,Layout.Alignment.ALIGN_CENTER)+11f
            rule(y,gold)
            y+=12f
            val kind=when(invoice.kind) {
                "repair"->"فاتورة صيانة"
                "purchase"->"فاتورة شراء"
                else->"فاتورة مبيع"
            }
            y+=block("$kind  ·  رقم #${invoice.number}",margin,y,(width-2*margin).toInt(),15f,ink,Layout.Alignment.ALIGN_CENTER,true)+9f
            y+=block("التاريخ: ${date.ifBlank { "—" }}",margin,y,(width-2*margin).toInt(),11f,muted)+4f
            y+=block("العميل: ${invoice.party.ifBlank { "عميل نقدي" }}",margin,y,(width-2*margin).toInt(),12f,ink)+11f
            val header=Paint().apply { color=light }
            page!!.canvas.drawRoundRect(margin,y,width-margin,y+31f,7f,7f,header)
            block("المنتج / الخدمة",margin+9,y+8,204,12f,ink,bold=true)
            block("الكمية",margin+221,y+8,50,11f,ink,Layout.Alignment.ALIGN_CENTER,true)
            block("المبلغ",margin+278,y+8,77,11f,ink,Layout.Alignment.ALIGN_CENTER,true)
            y+=39f
        }
        try {
            nextPage()
            for(item in items) {
                val name=item.getValue("name")
                val qty=item.getValue("qty")
                val amount=Money(item.getValue("net").toLong(),invoice.currency).display()
                val probe=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface=font;textSize=12f }
                val itemHeight=StaticLayout.Builder.obtain(name,0,name.length,probe,204)
                    .setAlignment(Layout.Alignment.ALIGN_OPPOSITE).setIncludePad(false).build().height
                val rowHeight=maxOf(33f,itemHeight+13f)
                if(y+rowHeight>height-130f)nextPage()
                block(name,margin+9,y+5,204,12f)
                block(qty,margin+221,y+5,50,12f,ink,Layout.Alignment.ALIGN_CENTER)
                block(amount,margin+278,y+5,77,11f,ink,Layout.Alignment.ALIGN_CENTER)
                y+=rowHeight
                rule(y)
                y+=5f
            }
            val totals=mutableListOf("الإجمالي" to Money(invoice.total,invoice.currency).display())
            if(invoice.kind!="purchase") {
                totals.add("المدفوع" to Money(store.paid(id),invoice.currency).display())
                val returns=store.credit(id)
                if(returns>0L)totals.add("المرتجعات" to Money(returns,invoice.currency).display())
                totals.add("المتبقي" to Money(store.due(id),invoice.currency).display())
                val refundable=store.refundable(id)
                if(refundable>0L)totals.add("مستحق الرد للعميل" to Money(refundable,invoice.currency).display())
            }
            if(y+totals.size*29+70>height)nextPage()
            y+=8f
            for((index,pair) in totals.withIndex()) {
                val important=index==0 || pair.first=="المتبقي"
                block(pair.first,margin+210,y,145,if(important)14f else 12f,
                    if(important)gold else muted,bold=important)
                block(pair.second,margin,y,194,if(important)14f else 12f,
                    if(important)ink else muted,Layout.Alignment.ALIGN_NORMAL,important)
                y+=28f
            }
            y+=13f
            rule(y,gold)
            y+=11f
            block("شكراً لثقتكم بالصفا للساعات",margin,y,(width-2*margin).toInt(),
                13f,gold,Layout.Alignment.ALIGN_CENTER,true)
            document.finishPage(page!!)
            document.writeTo(output)
        } finally { document.close() }
    }
}
