package com.alsafa.native8.sales

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.util.UUID

/** Original 80mm two-column text receipt: header, labelled rows, thank-you footer. */
data class InvoiceReceipt(val title:String,val number:String,val phone:String,val rows:List<Pair<String,String>>) {
 companion object {
  @Volatile private var cachedFont:Typeface?=null
  @Synchronized private fun font(context:Context):Typeface=cachedFont ?: Typeface.createFromAsset(context.assets,"fonts/Tajawal-Medium.ttf").also{cachedFont=it}
 }
 val plain:String get()="الصفا للساعات\n$title #$number\n"+rows.joinToString("\n"){"${it.first}: ${it.second}"}+"\nشكراً لثقتكم بنا"
 fun pdf(context:Context):File {
  val dir=File(context.cacheDir,"receipts").apply{mkdirs()}
  val target=File(dir,"AlSafa-${UUID.randomUUID()}.pdf")
  val logo=loadLogo(context)
  val doc=PdfDocument()
  var failure:Throwable?=null
  try {
   rows.chunked(24).forEachIndexed { index,part ->
    val height=pageHeight(context,part)
    val page=doc.startPage(PdfDocument.PageInfo.Builder(227,height,index+1).create())
    try {draw(context,page.canvas,part,height,logo)}
    catch(e:Throwable){runCatching{doc.finishPage(page)}.exceptionOrNull()?.let(e::addSuppressed);throw e}
    doc.finishPage(page)
   }
   target.outputStream().use(doc::writeTo)
  } catch(e:Throwable){failure=e;target.delete();throw e}
  finally {
   try {doc.close()}catch(close:Throwable){if(failure!=null)failure.addSuppressed(close) else throw close}
   finally {logo?.recycle()}
  }
  return target
 }
 fun bitmaps(context:Context,width:Int):Sequence<Bitmap> = sequence {
  for(part in rows.chunked(24)) {
   val height=pageHeight(context,part)
   val bitmap=Bitmap.createBitmap(width,(height*width/227f).toInt(),Bitmap.Config.ARGB_8888)
   val canvas=Canvas(bitmap);canvas.scale(width/227f,width/227f);val logo=loadLogo(context)
   try {draw(context,canvas,part,height,logo)}catch(e:Throwable){bitmap.recycle();throw e}finally{logo?.recycle()}
   yield(bitmap)
  }
 }
 private fun text(context:Context,value:String,width:Int,size:Float):StaticLayout {
  val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=size;typeface=font(context)}
  return StaticLayout.Builder.obtain(value,0,value.length,paint,width).setAlignment(Layout.Alignment.ALIGN_NORMAL)
   .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL).setIncludePad(false).build()
 }
 private fun rowHeight(context:Context,r:Pair<String,String>)=maxOf(text(context,r.first,91,8f).height,text(context,r.second,91,8f).height)+14
 private fun pageHeight(context:Context,part:List<Pair<String,String>>)=maxOf(411,180+part.sumOf{rowHeight(context,it)})
 private fun loadLogo(context:Context):Bitmap?=try {context.assets.open("invoice-logo.png").use {BitmapFactory.decodeStream(it)}}catch(_:java.io.IOException){null}
 private fun draw(context:Context,canvas:Canvas,part:List<Pair<String,String>>,height:Int,logo:Bitmap?) {
  canvas.drawColor(Color.WHITE)
  val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;strokeWidth=0.7f;typeface=font(context);textAlign=Paint.Align.CENTER}
  if(logo!=null)canvas.drawBitmap(logo,null,RectF(96.5f,8.5f,130.5f,51f),paint)
  paint.textSize=15f;canvas.drawText("الصفا للساعات",113.5f,66f,paint)
  paint.textSize=9f;canvas.drawText("Alsafa Watches",113.5f,80f,paint)
  canvas.drawLine(17f,88f,210f,88f,paint)
  paint.textSize=12f;canvas.drawText(title,113.5f,103f,paint);canvas.drawLine(17f,114f,210f,114f,paint)
  var y=126f
  for(r in part) {
   val label=text(context,r.first,91,8f);val value=text(context,r.second,91,8f)
   canvas.save();canvas.translate(119f,y);label.draw(canvas);canvas.restore()
   canvas.save();canvas.translate(17f,y);value.draw(canvas);canvas.restore()
   y+=rowHeight(context,r);paint.color=Color.LTGRAY;canvas.drawLine(17f,y-6f,210f,y-6f,paint);paint.color=Color.BLACK
  }
  paint.textSize=10f;canvas.drawText("شكراً لثقتكم بنا",113.5f,minOf(y+17,height-14f),paint)
 }
}
