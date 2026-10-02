package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/** Applies REPAIR_PART_DIRECT sale payloads on another device without creating an outbox loop. */
public final class DirectRepairPartRemoteApplier {
 private DirectRepairPartRemoteApplier(){}
 public static boolean applies(JSONObject j){return "REPAIR_PART_DIRECT".equals(j.optString("saleType"));}
 public static void apply(SQLiteDatabase db,String operationId,String recordId,JSONObject j){
  String saleId=j.optString("saleId",recordId),partId=j.optString("partId"),name=j.optString("partName","قطعة صيانة");
  if(saleId.isEmpty()||partId.isEmpty()||!j.has("afterQty"))throw new IllegalArgumentException("بيانات بيع قطعة الصيانة المباشر غير مكتملة");
  int qty=Math.max(1,j.optInt("quantity",1)),before=j.optInt("beforeQty"),after=j.optInt("afterQty");
  double unitCost=j.optDouble("unitCost"),unitPrice=j.optDouble("unitPrice"),partsTotal=j.optDouble("partsTotal",unitPrice*qty),serviceFee=j.optDouble("serviceFee"),total=j.optDouble("total",partsTotal+serviceFee);
  String currency=j.optString("currency","USD"),pay=j.optString("paymentMethod","CASH");long now=j.optLong("createdAt",System.currentTimeMillis());
  ContentValues p=new ContentValues();p.put("quantity",after);p.put("updated_at",now);if(db.update("repair_inventory",p,"part_id=? AND deleted_at IS NULL",new String[]{partId})!=1)throw new IllegalStateException("قطعة الصيانة غير موجودة على الجهاز المستقبل");
  ContentValues sm=new ContentValues();sm.put("move_id",saleId+"-stock");sm.put("part_id",partId);sm.put("source_type","DIRECT_SALE");sm.put("source_id",saleId);sm.put("before_qty",before);sm.put("delta_qty",-qty);sm.put("after_qty",after);sm.put("created_at",now);db.insertWithOnConflict("repair_inventory_moves",null,sm,SQLiteDatabase.CONFLICT_IGNORE);
  if(total>0&&!"LATER".equals(pay)){ContentValues c=new ContentValues();c.put("move_id","remote-partsale-cash-"+operationId);c.put("source_type","REPAIR_PART_DIRECT");c.put("source_id",saleId);c.put("direction","IN");c.put("amount",total);c.put("currency",currency);c.put("payment_method",pay);c.put("drawer",drawer(pay));c.put("created_at",now);db.insertWithOnConflict("cash_moves",null,c,SQLiteDatabase.CONFLICT_IGNORE);}
 }
 private static String drawer(String p){if("SHAM_CASH".equals(p))return "SHAM_CASH";if("BANK".equals(p))return "BANK";if("EXTERNAL".equals(p))return "EXTERNAL";return "MAIN";}
}
