package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import com.alsafa.native4.sync.SyncOperation;
import org.json.JSONObject;
import java.util.UUID;

/** Unified after-sales timeline for sale/repair records. */
public final class AlsafaCareStore {
 private final CoreStore.DbProvider provider;private final String deviceId;
 public AlsafaCareStore(CoreStore.DbProvider provider,String deviceId){this.provider=provider;this.deviceId=deviceId;}
 public String add(String customerId,String sourceType,String sourceId,String title,String details,String status)throws Exception{String type=req(sourceType,"نوع المصدر").toUpperCase(),sid=req(sourceId,"مرجع العملية"),t=req(title,"العنوان"),s=req(status,"الحالة").toUpperCase();if(!type.equals("SALE")&&!type.equals("REPAIR"))throw new IllegalArgumentException("مصدر AlSafa Care غير صالح");long now=System.currentTimeMillis();String id="care-"+UUID.randomUUID();SQLiteDatabase db=provider.db();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("care_id",id);v.put("customer_id",trim(customerId));v.put("source_type",type);v.put("source_id",sid);v.put("title",t);v.put("details",trim(details));v.put("status",s);v.put("created_at",now);v.put("updated_at",now);db.insertOrThrow("alsafa_care",null,v);JSONObject j=new JSONObject().put("careId",id).put("customerId",trim(customerId)).put("sourceType",type).put("sourceId",sid).put("title",t).put("details",trim(details)).put("status",s).put("updatedAt",now);provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.SETTING,id,j.toString(),deviceId));db.setTransactionSuccessful();return id;}finally{db.endTransaction();}}
 public void updateStatus(String id,String status)throws Exception{String s=req(status,"الحالة").toUpperCase();long now=System.currentTimeMillis();SQLiteDatabase db=provider.db();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("status",s);v.put("updated_at",now);if(db.update("alsafa_care",v,"care_id=?",new String[]{id})!=1)throw new IllegalArgumentException("سجل AlSafa Care غير موجود");JSONObject j=new JSONObject().put("careId",id).put("status",s).put("updatedAt",now);provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.SETTING,id,j.toString(),deviceId));db.setTransactionSuccessful();}finally{db.endTransaction();}}
 private static String req(String v,String label){String s=trim(v);if(s.isEmpty())throw new IllegalArgumentException(label+" مطلوب");return s;}private static String trim(String v){return v==null?"":v.trim();}
}
