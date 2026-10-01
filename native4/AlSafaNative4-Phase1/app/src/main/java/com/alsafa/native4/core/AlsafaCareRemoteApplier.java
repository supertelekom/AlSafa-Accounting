package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/** Applies AlSafa Care SETTING payloads without creating a return Outbox event. */
final class AlsafaCareRemoteApplier {
 private AlsafaCareRemoteApplier(){}
 static boolean tryApply(SQLiteDatabase db,Native4SyncReceiver.RemoteOp op,JSONObject j){
  if(!"SETTING".equals(op.kind)||!j.has("careId"))return false;
  String id=j.optString("careId",op.recordId);long now=j.optLong("updatedAt",System.currentTimeMillis());
  if(j.has("title")||j.has("sourceType")||j.has("sourceId")){
   ContentValues v=new ContentValues();v.put("care_id",id);v.put("customer_id",j.optString("customerId",""));v.put("source_type",j.optString("sourceType"));v.put("source_id",j.optString("sourceId"));v.put("title",j.optString("title"));v.put("details",j.optString("details",""));v.put("status",j.optString("status","OPEN"));v.put("created_at",j.optLong("createdAt",now));v.put("updated_at",now);db.insertWithOnConflict("alsafa_care",null,v,SQLiteDatabase.CONFLICT_REPLACE);
  }else if(j.has("status")){
   ContentValues v=new ContentValues();v.put("status",j.optString("status"));v.put("updated_at",now);if(db.update("alsafa_care",v,"care_id=?",new String[]{id})!=1)throw new IllegalStateException("سجل AlSafa Care غير موجود على الجهاز المستقبل");
  }else throw new IllegalArgumentException("بيانات AlSafa Care غير مكتملة");
  return true;
 }
}
