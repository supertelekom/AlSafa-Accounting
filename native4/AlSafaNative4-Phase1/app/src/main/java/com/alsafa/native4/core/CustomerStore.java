package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.alsafa.native4.sync.SyncOperation;
import org.json.JSONObject;
import java.util.UUID;

/** Customers + loyalty. Every mutation is local-first and committed with its Outbox operation. */
public final class CustomerStore {
 private final CoreStore.DbProvider provider; private final String deviceId;
 public CustomerStore(CoreStore.DbProvider provider,String deviceId){this.provider=provider;this.deviceId=deviceId;}
 public String save(String name,String phone,String notes)throws Exception{
  String n=req(name,"اسم العميل"),p=req(phone,"رقم العميل"); long now=System.currentTimeMillis(); SQLiteDatabase db=provider.db();db.beginTransaction();try{
   String id=null;try(Cursor c=db.rawQuery("SELECT customer_id FROM customers WHERE phone=? AND deleted_at IS NULL LIMIT 1",new String[]{p})){if(c.moveToFirst())id=c.getString(0);}boolean create=id==null;if(create)id="customer-"+UUID.randomUUID();ContentValues v=new ContentValues();v.put("name",n);v.put("phone",p);v.put("updated_at",now);if(create){v.put("customer_id",id);v.put("created_at",now);db.insertOrThrow("customers",null,v);}else db.update("customers",v,"customer_id=?",new String[]{id});
   JSONObject j=new JSONObject().put("customerId",id).put("name",n).put("phone",p).put("notes",trim(notes)).put("updatedAt",now);provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.SETTING,id,j.toString(),deviceId));db.setTransactionSuccessful();return id;
  }finally{db.endTransaction();}}
 public int addPoints(String customerId,int points,String sourceType,String sourceId)throws Exception{return changePoints(customerId,Math.abs(points),sourceType,sourceId);}
 public int usePoints(String customerId,int points,String sourceType,String sourceId)throws Exception{return changePoints(customerId,-Math.abs(points),sourceType,sourceId);}
 private int changePoints(String customerId,int delta,String sourceType,String sourceId)throws Exception{
  if(delta==0)throw new IllegalArgumentException("النقاط يجب ألا تكون صفراً");SQLiteDatabase db=provider.db();db.beginTransaction();try{int before;try(Cursor c=db.rawQuery("SELECT points FROM customers WHERE customer_id=? AND deleted_at IS NULL",new String[]{customerId})){if(!c.moveToFirst())throw new IllegalArgumentException("العميل غير موجود");before=(int)Math.round(c.getDouble(0));}int after=before+delta;if(after<0)throw new IllegalArgumentException("نقاط العميل غير كافية");long now=System.currentTimeMillis();ContentValues cv=new ContentValues();cv.put("points",after);cv.put("updated_at",now);db.update("customers",cv,"customer_id=?",new String[]{customerId});String id="loyalty-"+UUID.randomUUID();ContentValues lv=new ContentValues();lv.put("move_id",id);lv.put("customer_id",customerId);lv.put("points",delta);lv.put("source_type",trim(sourceType));lv.put("source_id",trim(sourceId));lv.put("created_at",now);db.insertOrThrow("loyalty_moves",null,lv);JSONObject j=new JSONObject().put("customerId",customerId).put("pointsDelta",delta).put("pointsAfter",after).put("sourceType",trim(sourceType)).put("sourceId",trim(sourceId)).put("updatedAt",now);provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.SETTING,id,j.toString(),deviceId));db.setTransactionSuccessful();return after;}finally{db.endTransaction();}}
 private static String req(String v,String label){String s=trim(v);if(s.isEmpty())throw new IllegalArgumentException(label+" مطلوب");return s;}private static String trim(String v){return v==null?"":v.trim();}
}
