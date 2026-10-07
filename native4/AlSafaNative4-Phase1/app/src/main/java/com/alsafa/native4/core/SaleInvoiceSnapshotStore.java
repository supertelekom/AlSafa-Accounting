package com.alsafa.native4.core;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only canonical invoice snapshot used by detail, PDF and sharing. */
public final class SaleInvoiceSnapshotStore {
 private final CoreStore.DbProvider provider;
 public SaleInvoiceSnapshotStore(CoreStore.DbProvider provider){this.provider=provider;}
 public static final class Item {
  public final String description;public final int quantity;public final double unitPrice,total;
  Item(String d,int q,double p,double t){description=d;quantity=q;unitPrice=p;total=t;}
 }
 public static final class Payment {
  public final double amount;public final String currency,method,account;
  Payment(double a,String c,String m,String ac){amount=a;currency=c;method=m;account=ac;}
 }
 public static final class Snapshot {
  public final String invoiceId,invoiceNo,currency,customerName,customerPhone,saleChannel,paymentStatus,warranty,notes;
  public final double subtotal,discount,tax,total;public final long businessAt;
  public final List<Item> items;public final List<Payment> payments;
  Snapshot(String id,String no,String cur,String cn,String cp,String sc,String ps,String w,String n,double sub,double dis,double tx,double tot,long at,List<Item> i,List<Payment> p){
   invoiceId=id;invoiceNo=no;currency=cur;customerName=cn;customerPhone=cp;saleChannel=sc;paymentStatus=ps;warranty=w;notes=n;subtotal=sub;discount=dis;tax=tx;total=tot;businessAt=at;items=Collections.unmodifiableList(i);payments=Collections.unmodifiableList(p);
  }
 }
 public Snapshot get(String invoiceId){
  SQLiteDatabase db=provider.db();String no,cur,cn,cp,sc,ps,w,n;double sub,dis,tx,tot;long at;
  try(Cursor c=db.rawQuery("SELECT invoice_no,currency,COALESCE(customer_name,''),COALESCE(customer_phone,''),COALESCE(sale_channel,'STORE'),COALESCE(payment_status,'PAID'),COALESCE(warranty_text,''),COALESCE(notes,''),subtotal,discount,COALESCE(tax_amount,0),total,COALESCE(business_at,created_at) FROM invoices WHERE invoice_id=? AND invoice_type='SALE'",new String[]{invoiceId})){
   if(!c.moveToFirst())throw new IllegalArgumentException("فاتورة المبيع غير موجودة");no=c.getString(0);cur=c.getString(1);cn=c.getString(2);cp=c.getString(3);sc=c.getString(4);ps=c.getString(5);w=c.getString(6);n=c.getString(7);sub=c.getDouble(8);dis=c.getDouble(9);tx=c.getDouble(10);tot=c.getDouble(11);at=c.getLong(12);
  }
  List<Item> items=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT COALESCE(description,''),quantity,unit_price,total FROM invoice_items WHERE invoice_id=? ORDER BY created_at,item_id",new String[]{invoiceId})){while(c.moveToNext())items.add(new Item(c.getString(0),c.getInt(1),c.getDouble(2),c.getDouble(3)));}
  List<Payment> payments=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT amount,currency,payment_method,account FROM split_payments WHERE source_type='SALE' AND source_id=? ORDER BY created_at,payment_id",new String[]{invoiceId})){while(c.moveToNext())payments.add(new Payment(c.getDouble(0),c.getString(1),c.getString(2),c.getString(3)));}
  return new Snapshot(invoiceId,no,cur,cn,cp,sc,ps,w,n,sub,dis,tx,tot,at,items,payments);
 }
}
