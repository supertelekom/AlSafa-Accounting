package com.alsafa.native6

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

/** Canvas/StaticLayout preserve Arabic shaping; no HTML renderer. */
class InvoicePdf(private val context:Context,private val store:Store) {
    fun write(id:String,output:OutputStream) {
        val invoice=store.invoice(id)
        val lines=mutableListOf("الصفا للساعات","فاتورة رقم ${invoice.number}","${invoice.party} · ${invoice.status}")
        store.rows("SELECT name,qty,net FROM invoice_lines WHERE invoice_id=? ORDER BY line",id).forEach{lines.add("${it.getValue("name")} × ${it.getValue("qty")}   ${Money(it.getValue("net").toLong(),invoice.currency).display()}")}
        lines.add("الإجمالي: ${Money(invoice.total,invoice.currency).display()}")
        if(invoice.kind!="purchase"){
            lines.add("المدفوع: ${Money(store.paid(id),invoice.currency).display()}")
            lines.add("قيمة المرتجعات: ${Money(store.credit(id),invoice.currency).display()}")
            lines.add("المتبقي: ${Money(store.due(id),invoice.currency).display()}")
            lines.add("المستحق رده: ${Money(store.refundable(id),invoice.currency).display()}")
        }
        val paint=TextPaint().apply{color=Color.rgb(62,52,41);textSize=16f;typeface=Typeface.createFromAsset(context.assets,"Tajawal-Medium.ttf");isAntiAlias=true}
        PdfDocument().use{doc->var number=1;var page=doc.startPage(PdfDocument.PageInfo.Builder(420,595,number).create());var y=30
            lines.forEach{line->val layout=StaticLayout.Builder.obtain(line,0,line.length,paint,360).setAlignment(Layout.Alignment.ALIGN_OPPOSITE).setIncludePad(true).build()
                if(y+layout.height>565){doc.finishPage(page);number++;page=doc.startPage(PdfDocument.PageInfo.Builder(420,595,number).create());y=30}
                page.canvas.save();page.canvas.translate(30f,y.toFloat());layout.draw(page.canvas);page.canvas.restore();y+=layout.height+12
            };doc.finishPage(page);doc.writeTo(output)
        }
    }
}
