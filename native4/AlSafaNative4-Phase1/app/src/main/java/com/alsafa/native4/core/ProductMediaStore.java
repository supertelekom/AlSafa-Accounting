package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import com.alsafa.native4.sync.SyncOperation;
import org.json.JSONObject;
import java.util.UUID;

/** Product images/barcodes. Local mutation and its Outbox command commit atomically. */
public final class ProductMediaStore {
 private final CoreStore.DbProvider provider;private final String deviceId;
 public ProductMediaStore(CoreStore.DbProvider provider,String deviceId){this.provider=provider;this.deviceId=deviceId;}
 public String addImage(String productId,String uri,int sortOrder)throws Exception{
  String pid=req(productId,"المنتج"),val=req(uri,"الصورة");long now=System.currentTimeMillis(),sort=Math.max(0,sortOrder);String id="image-"+UUID.randomUUID();SQLiteDatabase db=provider.db();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("image_id",id);v.put("product_id",pid);v.put("local_uri",val);v.put("sort_order",sort);v.put("created_at",now);db.insertOrThrow("product_images",null,v);JSONObject j=new JSONObject().put("assetType","IMAGE").put("imageId",id).put("productId",pid).put("localUri",val).put("sortOrder",sort).put("updatedAt",now);sync(db,id,j);db.setTransactionSuccessful();return id;}finally{db.endTransaction();}}
 public String addBarcode(String productId,String barcode,boolean customerQr)throws Exception{
  String pid=req(productId,"المنتج"),b=req(barcode,"الباركود"),kind=customerQr?"CUSTOMER_QR":"SELLER";long now=System.currentTimeMillis();SQLiteDatabase db=provider.db();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("barcode",b);v.put("product_id",pid);v.put("kind",kind);v.put("created_at",now);db.insertOrThrow("product_barcodes",null,v);JSONObject j=new JSONObject().put("assetType","BARCODE").put("barcode",b).put("productId",pid).put("kind",kind).put("updatedAt",now);sync(db,b,j);db.setTransactionSuccessful();return b;}finally{db.endTransaction();}}
 private void sync(SQLiteDatabase db,String id,JSONObject j)throws Exception{provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.PRODUCT_UPDATE,id,j.toString(),deviceId));}
 private static String req(String v,String l){String s=trim(v);if(s.isEmpty())throw new IllegalArgumentException(l+" مطلوب");return s;}private static String trim(String v){return v==null?"":v.trim();}
}
