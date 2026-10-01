package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/** Applies product images/barcodes received from another device without creating a new Outbox event. */
public final class ProductRemoteApplier {
 private ProductRemoteApplier(){}
 public static boolean applyAuxiliary(SQLiteDatabase db,JSONObject j){
  long now=j.optLong("updatedAt",System.currentTimeMillis());
  if(j.has("imageId")){
   String id=j.optString("imageId"),productId=j.optString("productId"),uri=j.optString("uri");
   if(id.isEmpty()||productId.isEmpty()||uri.isEmpty())throw new IllegalArgumentException("بيانات صورة المنتج غير مكتملة");
   ContentValues v=new ContentValues();v.put("image_id",id);v.put("product_id",productId);v.put("local_uri",uri);v.put("sort_order",j.optInt("sortOrder",0));v.put("created_at",now);v.putNull("deleted_at");
   db.insertWithOnConflict("product_images",null,v,SQLiteDatabase.CONFLICT_REPLACE);return true;
  }
  if(j.has("barcodeId")){
   String productId=j.optString("productId"),barcode=j.optString("barcode");
   if(productId.isEmpty()||barcode.isEmpty())throw new IllegalArgumentException("بيانات باركود المنتج غير مكتملة");
   ContentValues v=new ContentValues();v.put("barcode",barcode);v.put("product_id",productId);v.put("kind",j.optString("kind","SELLER"));v.put("created_at",now);v.putNull("deleted_at");
   db.insertWithOnConflict("product_barcodes",null,v,SQLiteDatabase.CONFLICT_REPLACE);return true;
  }
  return false;
 }
}
