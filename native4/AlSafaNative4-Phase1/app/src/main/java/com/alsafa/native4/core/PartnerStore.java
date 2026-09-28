package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import com.alsafa.native4.sync.SyncOperation;
import org.json.JSONObject;
import java.util.UUID;

/** External product partners: isolated capital/profit ownership, never mixed with store capital. */
public final class PartnerStore {
 private final CoreStore.DbProvider provider;private final String deviceId;
 public PartnerStore(CoreStore.DbProvider provider,String deviceId){this.provider=provider;this.deviceId=deviceId;}
 public String savePartner(String partnerId,String name,double profitPercent)throws Exception{String n=req(name,"اسم الشريك");if(!Double.isFinite(profitPercent)||profitPercent<0||profitPercent>100)throw new IllegalArgumentException("نسبة الربح غير صالحة");String id=trim(partnerId);boolean create=id.isEmpty();if(create)id="partner-"+UUID.randomUUID();long now=System.currentTimeMillis();SQLiteDatabase db=provider.db();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("name",n);v.put("profit_percent",profitPercent);v.put("updated_at",now);if(create){v.put("partner_id",id);v.put("created_at",now);db.insertOrThrow("partners",null,v);}else if(db.update("partners",v,"partner_id=? AND deleted_at IS NULL",new String[]{id})!=1)throw new IllegalArgumentException("الشريك غير موجود");JSONObject j=new JSONObject().put("partnerId",id).put("name",n).put("profitPercent",profitPercent).put("updatedAt",now);provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.SETTING,id,j.toString(),deviceId));db.setTransactionSuccessful();return id;}finally{db.endTransaction();}}
 public void assignProduct(String partnerId,String productId,double capital,String currency)throws Exception{if(!Double.isFinite(capital)||capital<0)throw new IllegalArgumentException("رأس المال غير صالح");String c=currency(currency);long now=System.currentTimeMillis();SQLiteDatabase db=provider.db();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("partner_id",partnerId);v.put("product_id",productId);v.put("capital",capital);v.put("currency",c);v.put("created_at",now);db.insertWithOnConflict("partner_products",null,v,SQLiteDatabase.CONFLICT_REPLACE);String id=partnerId+":"+productId;JSONObject j=new JSONObject().put("partnerId",partnerId).put("productId",productId).put("capital",capital).put("currency",c).put("updatedAt",now);provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.SETTING,id,j.toString(),deviceId));db.setTransactionSuccessful();}finally{db.endTransaction();}}
 private static String currency(String v){String x=trim(v).toUpperCase();return x.equals("TRY")||x.equals("SYP")?x:"USD";}private static String req(String v,String label){String s=trim(v);if(s.isEmpty())throw new IllegalArgumentException(label+" مطلوب");return s;}private static String trim(String v){return v==null?"":v.trim();}
}
