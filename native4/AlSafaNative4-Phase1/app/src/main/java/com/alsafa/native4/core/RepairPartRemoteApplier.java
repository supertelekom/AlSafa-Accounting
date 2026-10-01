package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/** Applies repair-inventory SETTING payloads without producing another Outbox operation. */
public final class RepairPartRemoteApplier {
 private RepairPartRemoteApplier(){}
 public static boolean apply(SQLiteDatabase db,String operationId,String recordId,JSONObject j){
  if(!j.has("partId"))return false;
  String partId=j.optString("partId",recordId);long now=j.optLong("updatedAt",System.currentTimeMillis());
  if(j.has("name")){
   ContentValues v=new ContentValues();v.put("part_id",partId);v.put("name",j.optString("name"));v.put("barcode",j.optString("barcode",""));v.put("quantity",j.optInt("quantity",0));v.put("unit_cost",j.has("unitCost")?j.optDouble("unitCost"):j.optDouble("cost",0));v.put("sale_price",j.optDouble("salePrice",0));v.put("currency",j.optString("currency","USD"));v.put("created_at",j.optLong("createdAt",now));v.put("updated_at",now);v.putNull("deleted_at");db.insertWithOnConflict("repair_inventory",null,v,SQLiteDatabase.CONFLICT_REPLACE);return true;
  }
  if(j.has("after")&&j.has("delta")){
   ContentValues q=new ContentValues();q.put("quantity",j.optInt("after"));q.put("updated_at",now);if(db.update("repair_inventory",q,"part_id=? AND deleted_at IS NULL",new String[]{partId})!=1)throw new IllegalStateException("قطعة الصيانة غير موجودة على الجهاز المستقبل");
   ContentValues m=new ContentValues();m.put("move_id",recordId);m.put("part_id",partId);m.put("source_type",j.optString("sourceType","SYNC"));m.put("source_id",j.optString("sourceId",""));m.put("before_qty",j.optInt("before"));m.put("delta_qty",j.optInt("delta"));m.put("after_qty",j.optInt("after"));m.put("created_at",now);db.insertWithOnConflict("repair_inventory_moves",null,m,SQLiteDatabase.CONFLICT_IGNORE);return true;
  }
  return false;
 }
}
