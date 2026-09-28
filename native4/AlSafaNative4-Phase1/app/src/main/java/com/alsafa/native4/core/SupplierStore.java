package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import com.alsafa.native4.sync.SyncOperation;
import org.json.JSONObject;
import java.util.UUID;

/** Supplier create/update. Deletion is never automatic. */
public final class SupplierStore {
 private final CoreStore.DbProvider provider; private final String deviceId;
 public SupplierStore(CoreStore.DbProvider provider,String deviceId){this.provider=provider;this.deviceId=deviceId;}
 public String save(String supplierId,String name,String phone,String notes)throws Exception{String n=req(name,"اسم المورد"),id=trim(supplierId);boolean create=id.isEmpty();if(create)id="supplier-"+UUID.randomUUID();long now=System.currentTimeMillis();SQLiteDatabase db=provider.db();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("name",n);v.put("phone",trim(phone));v.put("notes",trim(notes));v.put("updated_at",now);if(create){v.put("supplier_id",id);v.put("created_at",now);db.insertOrThrow("suppliers",null,v);}else if(db.update("suppliers",v,"supplier_id=? AND deleted_at IS NULL",new String[]{id})!=1)throw new IllegalArgumentException("المورد غير موجود");JSONObject j=new JSONObject().put("supplierId",id).put("name",n).put("phone",trim(phone)).put("notes",trim(notes)).put("updatedAt",now);provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.SETTING,id,j.toString(),deviceId));db.setTransactionSuccessful();return id;}finally{db.endTransaction();}}
 private static String req(String v,String l){String s=trim(v);if(s.isEmpty())throw new IllegalArgumentException(l+" مطلوب");return s;}private static String trim(String v){return v==null?"":v.trim();}
}
