package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/** Applies reservation SETTING operations without creating another outbox event. */
public final class ReservationRemoteApplier {
 private ReservationRemoteApplier(){}
 public static boolean tryApply(SQLiteDatabase db,Native4SyncReceiver.RemoteOp op)throws Exception{
  if(!"SETTING".equals(op.kind))return false;
  JSONObject j=new JSONObject(op.payload);
  if(!j.has("reservationId"))return false;
  String id=j.optString("reservationId",op.recordId);
  if(id==null||id.trim().isEmpty())throw new IllegalArgumentException("معرف الحجز مفقود");
  long now=j.optLong("updatedAt",System.currentTimeMillis());
  if(j.has("customerName")||j.has("phone")||j.has("deposit")){
   ContentValues v=new ContentValues();
   v.put("reservation_id",id);
   v.put("customer_name",j.optString("customerName","عميل"));
   v.put("phone",j.optString("phone",""));
   v.put("product_id",j.optString("productId",""));
   v.put("deposit",j.optDouble("deposit",0));
   v.put("currency",j.optString("currency","USD"));
   v.put("status",j.optString("status","OPEN"));
   v.put("notes",j.optString("notes",""));
   v.put("created_at",j.optLong("createdAt",now));
   v.put("updated_at",now);
   db.insertWithOnConflict("reservations",null,v,SQLiteDatabase.CONFLICT_REPLACE);
   return true;
  }
  if(j.has("status")){
   ContentValues v=new ContentValues();v.put("status",j.optString("status"));v.put("updated_at",now);
   if(db.update("reservations",v,"reservation_id=?",new String[]{id})!=1)throw new IllegalStateException("الحجز غير موجود على الجهاز المستقبل");
   return true;
  }
  throw new IllegalArgumentException("بيانات الحجز المستقبلة غير مكتملة");
 }
}
