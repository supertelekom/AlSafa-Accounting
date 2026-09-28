package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import com.alsafa.native4.sync.SyncOperation;
import org.json.JSONObject;
import java.util.UUID;

/** Reservations with local-first durable synchronization. */
public final class ReservationStore {
 private final CoreStore.DbProvider provider;private final String deviceId;
 public ReservationStore(CoreStore.DbProvider provider,String deviceId){this.provider=provider;this.deviceId=deviceId;}
 public String create(String customerName,String phone,String productId,double deposit,String currency,String notes)throws Exception{
  String name=req(customerName,"اسم العميل"),p=req(phone,"الهاتف"),c=currency(currency);if(!Double.isFinite(deposit)||deposit<0)throw new IllegalArgumentException("العربون غير صالح");long now=System.currentTimeMillis();String id="reservation-"+UUID.randomUUID();SQLiteDatabase db=provider.db();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("reservation_id",id);v.put("customer_name",name);v.put("phone",p);v.put("product_id",trim(productId));v.put("deposit",deposit);v.put("currency",c);v.put("status","OPEN");v.put("notes",trim(notes));v.put("created_at",now);v.put("updated_at",now);db.insertOrThrow("reservations",null,v);JSONObject j=new JSONObject().put("reservationId",id).put("customerName",name).put("phone",p).put("productId",trim(productId)).put("deposit",deposit).put("currency",c).put("status","OPEN").put("notes",trim(notes)).put("updatedAt",now);provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.SETTING,id,j.toString(),deviceId));db.setTransactionSuccessful();return id;}finally{db.endTransaction();}}
 public void setStatus(String id,String status)throws Exception{String s=trim(status).toUpperCase();if(!s.equals("OPEN")&&!s.equals("COMPLETED")&&!s.equals("CANCELLED"))throw new IllegalArgumentException("حالة الحجز غير صالحة");long now=System.currentTimeMillis();SQLiteDatabase db=provider.db();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("status",s);v.put("updated_at",now);if(db.update("reservations",v,"reservation_id=?",new String[]{id})!=1)throw new IllegalArgumentException("الحجز غير موجود");JSONObject j=new JSONObject().put("reservationId",id).put("status",s).put("updatedAt",now);provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.SETTING,id,j.toString(),deviceId));db.setTransactionSuccessful();}finally{db.endTransaction();}}
 private static String currency(String v){String x=trim(v).toUpperCase();return x.equals("TRY")||x.equals("SYP")?x:"USD";}private static String req(String v,String label){String s=trim(v);if(s.isEmpty())throw new IllegalArgumentException(label+" مطلوب");return s;}private static String trim(String v){return v==null?"":v.trim();}
}
