package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.alsafa.native4.sync.SyncOperation;
import org.json.JSONObject;
import java.util.UUID;

/** Additional colors are real independent products, linked only for presentation. */
public final class ProductVariantStore {
 private final CoreStore.DbProvider provider;private final String deviceId;
 public ProductVariantStore(CoreStore.DbProvider provider,String deviceId){this.provider=provider;this.deviceId=deviceId;}
 public String createAdditional(String parentId,String name,String ref,String barcode,String brand,String dialColor,String caseColor,String strapColor,int quantity,double salePrice,double costPrice,String currency)throws Exception{
  String parent=req(parentId,"المنتج الأساسي"),n=req(name,"اسم المنتج");ensureParent(parent);if(quantity<0||salePrice<0||costPrice<0)throw new IllegalArgumentException("الكمية أو السعر غير صالح");String cur=currency(currency),id="product-"+UUID.randomUUID();long now=System.currentTimeMillis();SQLiteDatabase db=provider.db();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("product_id",id);v.put("name",n);v.put("ref",trim(ref));v.put("barcode",trim(barcode));v.put("brand",trim(brand));v.put("variant_type","ADDITIONAL");v.put("parent_product_id",parent);v.put("dial_color",trim(dialColor));v.put("case_color",trim(caseColor));v.put("strap_color",trim(strapColor));v.put("quantity",quantity);v.put("sale_price",salePrice);v.put("cost_price",costPrice);v.put("currency",cur);v.put("created_at",now);v.put("updated_at",now);db.insertOrThrow("products",null,v);JSONObject j=new JSONObject().put("productId",id).put("parentProductId",parent).put("variantType","ADDITIONAL").put("name",n).put("ref",trim(ref)).put("barcode",trim(barcode)).put("brand",trim(brand)).put("dialColor",trim(dialColor)).put("caseColor",trim(caseColor)).put("strapColor",trim(strapColor)).put("quantity",quantity).put("salePrice",salePrice).put("costPrice",costPrice).put("currency",cur).put("updatedAt",now);provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.PRODUCT_CREATE,id,j.toString(),deviceId));db.setTransactionSuccessful();return id;}finally{db.endTransaction();}}
 private void ensureParent(String id){try(Cursor c=provider.db().rawQuery("SELECT 1 FROM products WHERE product_id=? AND deleted_at IS NULL LIMIT 1",new String[]{id})){if(!c.moveToFirst())throw new IllegalArgumentException("المنتج الأساسي غير موجود");}}
 private static String currency(String v){String x=trim(v).toUpperCase();return x.equals("TRY")||x.equals("SYP")?x:"USD";}private static String req(String v,String l){String s=trim(v);if(s.isEmpty())throw new IllegalArgumentException(l+" مطلوب");return s;}private static String trim(String v){return v==null?"":v.trim();}
}
