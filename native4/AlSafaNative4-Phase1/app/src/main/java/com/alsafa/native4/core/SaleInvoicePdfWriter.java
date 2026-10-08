package com.alsafa.native4.core;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Read-only renderer: no accounting, inventory or synchronization writes. */
public final class SaleInvoicePdfWriter {
 private SaleInvoicePdfWriter(){}
 private static final int WIDTH=595, HEIGHT=842, TOP=48, BOTTOM=790;
 private static final class Pages {
  final PdfDocument document=new PdfDocument();
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  PdfDocument.Page page;
  int y,number;
  Pages(){paint.setTextSize(12);paint.setTextAlign(Paint.Align.RIGHT);next();}
  void next(){
   if(page!=null)finishPage();
   page=document.startPage(new PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,++number).create());
   y=TOP;
   paint.setFakeBoldText(true);
   page.getCanvas().drawText("الصفا للساعات",555,y,paint);
   paint.setFakeBoldText(false);
   y+=28;
  }
  void finishPage(){
   paint.setTextSize(10);paint.setTextAlign(Paint.Align.CENTER);
   page.getCanvas().drawText("صفحة "+number,WIDTH/2f,HEIGHT-25,paint);
   paint.setTextSize(12);paint.setTextAlign(Paint.Align.RIGHT);
   document.finishPage(page);page=null;
  }
  void line(String value){
   String text=value==null?"":value.replace('\r',' ').replace('\t',' ');
   if(text.isEmpty()){y+=22;return;}
   for(String paragraph:text.split("\\n",-1)){
    String remaining=paragraph;
    if(remaining.isEmpty()){if(y>BOTTOM)next();y+=22;continue;}
    while(!remaining.isEmpty()){
     if(y>BOTTOM)next();
     int count=paint.breakText(remaining,true,495,null);
     if(count<=0)throw new IllegalStateException("تعذر تنسيق نص الفاتورة");
     if(count<remaining.length() && Character.isHighSurrogate(remaining.charAt(count-1)))count--;
     if(count<=0)throw new IllegalStateException("تعذر تقسيم نص الفاتورة");
     if(count<remaining.length()){
      int space=remaining.lastIndexOf(' ',count-1);
      if(space>0 && space>count/2)count=space+1;
     }
     page.getCanvas().drawText(remaining.substring(0,count).trim(),555,y,paint);
     remaining=remaining.substring(count);
     y+=22;
    }
   }
  }
  void close(){if(page!=null)finishPage();document.close();}
 }
 public static File write(Context context,SaleInvoiceSnapshotStore.Snapshot x)throws Exception{
  if(context==null||x==null)throw new IllegalArgumentException("بيانات الفاتورة مطلوبة");
  File dir=new File(context.getCacheDir(),"invoices");
  if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("تعذر إنشاء مجلد الفاتورة");
  File out=File.createTempFile("invoice-"+safe(x.invoiceNo)+"-",".pdf",dir);
  Pages p=new Pages();boolean success=false;
  try{
   p.line("فاتورة مبيع");
   p.line("رقم الفاتورة: "+x.invoiceNo);
   p.line("التاريخ: "+new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date(x.businessAt)));
   p.line("العميل: "+(x.customerName.isEmpty()?"بدون عميل محدد":x.customerName));
   if(!x.customerPhone.isEmpty())p.line("الهاتف: "+x.customerPhone);
   p.line("الأصناف");
   for(SaleInvoiceSnapshotStore.Item i:x.items)p.line(i.description+" | "+i.quantity+" × "+money(i.unitPrice)+" = "+money(i.total));
   p.line("المجموع: "+money(x.subtotal)+" "+x.currency);
   p.line("الخصم: "+money(x.discount));
   p.line("الضريبة: "+money(x.tax));
   p.line("الإجمالي: "+money(x.total)+" "+x.currency);
   p.line("حالة الدفع: "+paymentLabel(x.paymentStatus));
   for(SaleInvoiceSnapshotStore.Payment pay:x.payments)p.line("دفعة: "+money(pay.amount)+" "+pay.currency+" — "+pay.method);
   if(!x.warranty.isEmpty())p.line("الضمان: "+x.warranty);
   if(!x.notes.isEmpty())p.line("ملاحظات: "+x.notes);
   p.finishPage();
   try(FileOutputStream fos=new FileOutputStream(out)){p.document.writeTo(fos);}
   success=true;return out;
  }finally{p.close();if(!success)out.delete();}
 }
 private static String paymentLabel(String state){if("PAID".equals(state))return "مدفوعة";if("PARTIAL".equals(state))return "مدفوعة جزئياً";if("DUE".equals(state))return "غير مدفوعة";return state==null?"غير محددة":state;}
 private static String money(double v){return String.format(Locale.US,"%.2f",v);}
 private static String safe(String s){return s==null||s.trim().isEmpty()?"sale":s.replaceAll("[^a-zA-Z0-9_-]","_");}
}
