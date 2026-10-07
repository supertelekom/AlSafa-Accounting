package com.alsafa.native4.core;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Read-only PDF renderer. It never changes accounting, stock, outbox or sync state. */
public final class SaleInvoicePdfWriter {
 private SaleInvoicePdfWriter(){}
 public static File write(Context context, SaleInvoiceSnapshotStore.Snapshot x)throws Exception{
  if(context==null||x==null)throw new IllegalArgumentException("بيانات الفاتورة مطلوبة");
  File dir=new File(context.getCacheDir(),"invoices");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("تعذر إنشاء مجلد الفاتورة");
  File out=new File(dir,"invoice-"+safe(x.invoiceNo)+"-"+System.currentTimeMillis()+".pdf");
  PdfDocument doc=new PdfDocument();PdfDocument.Page page=null;try{
   PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,1).create();page=doc.startPage(info);
   Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(18);p.setFakeBoldText(true);p.setTextAlign(Paint.Align.CENTER);page.getCanvas().drawText("الصفا للساعات",297,55,p);
   p.setTextSize(12);page.getCanvas().drawText("فاتورة مبيع",297,78,p);
   p.setFakeBoldText(false);p.setTextAlign(Paint.Align.RIGHT);int y=112;
   y=line(page,p,"رقم الفاتورة: "+x.invoiceNo,555,y);y=line(page,p,"التاريخ: "+new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date(x.businessAt)),555,y);
   y=line(page,p,"العميل: "+(x.customerName.isEmpty()?"بدون عميل محدد":x.customerName),555,y);
   if(!x.customerPhone.isEmpty())y=line(page,p,"الهاتف: "+x.customerPhone,555,y);
   y+=18; y=line(page,p,"الأصناف",555,y);
   for(SaleInvoiceSnapshotStore.Item i:x.items){y=line(page,p,i.description+"  |  "+i.quantity+" × "+money(i.unitPrice)+" = "+money(i.total),555,y);if(y>780)break;}
   y+=16;y=line(page,p,"المجموع: "+money(x.subtotal)+" "+x.currency,555,y);y=line(page,p,"الخصم: "+money(x.discount),555,y);y=line(page,p,"الضريبة: "+money(x.tax),555,y);y=line(page,p,"الإجمالي: "+money(x.total)+" "+x.currency,555,y);
   y+=16;y=line(page,p,"حالة الدفع: "+x.paymentStatus,555,y);
   for(SaleInvoiceSnapshotStore.Payment pay:x.payments)y=line(page,p,"دفعة: "+money(pay.amount)+" "+pay.currency+" — "+pay.method,555,y);
   if(!x.warranty.isEmpty())y=line(page,p,"الضمان: "+x.warranty,555,y);
   if(!x.notes.isEmpty())line(page,p,"ملاحظات: "+x.notes,555,y);
   doc.finishPage(page);page=null;try(FileOutputStream fos=new FileOutputStream(out)){doc.writeTo(fos);}return out;
  }finally{if(page!=null)doc.finishPage(page);doc.close();}
 }
 private static int line(PdfDocument.Page page,Paint p,String text,float x,int y){page.getCanvas().drawText(text,x,y,p);return y+22;}
 private static String money(double v){return String.format(Locale.US,"%.2f",v);}
 private static String safe(String s){return s==null||s.trim().isEmpty()?"sale":s.replaceAll("[^a-zA-Z0-9_-]","_");}
}
