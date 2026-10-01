package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/** Applies SALE_UPDATE online settlement operations without producing another outbox event. */
final class OnlineSaleRemoteApplier {
 private OnlineSaleRemoteApplier(){}
 static void apply(SQLiteDatabase db,Native4SyncReceiver.RemoteOp op)throws Exception{
  JSONObject j=new JSONObject(op.payload);String settlementId=j.optString("settlementId","").trim();
  if(settlementId.isEmpty())throw new IllegalArgumentException("معرف تسوية بيع الإنترنت مفقود");
  if(j.has("collectionAmount"))applyCollection(db,op,j,settlementId);else applySettlement(db,op,j,settlementId);
 }
 private static void applySettlement(SQLiteDatabase db,Native4SyncReceiver.RemoteOp op,JSONObject j,String id){
  String saleId=j.optString("saleId",op.recordId),currency=j.optString("currency","USD"),shippingCurrency=j.optString("shippingCurrency",currency);long now=j.optLong("updatedAt",System.currentTimeMillis());double paid=j.optDouble("paidTotal"),due=j.optDouble("totalDue"),remaining=j.has("remaining")?j.optDouble("remaining"):Math.max(0,due-paid);
  ContentValues v=new ContentValues();v.put("settlement_id",id);v.put("sale_id",saleId);v.put("merchandise_total",j.optDouble("merchandiseTotal"));v.put("currency",currency);v.put("shipping_amount",j.optDouble("shippingAmount"));v.put("shipping_currency",shippingCurrency);v.put("shipping_payer",j.optString("shippingPayer","SELLER"));v.put("total_due",due);v.put("paid_total",paid);v.put("remaining",remaining);v.put("created_at",now);v.put("updated_at",now);db.insertWithOnConflict("online_sale_settlements",null,v,SQLiteDatabase.CONFLICT_IGNORE);
  if(paid>0)payment(db,"remote-online-initial-"+op.id,id,paid,currency,j.optString("paymentMethod","CASH"),"INITIAL",now);
 }
 private static void applyCollection(SQLiteDatabase db,Native4SyncReceiver.RemoteOp op,JSONObject j,String id){
  double due;String base;try(Cursor c=db.rawQuery("SELECT total_due,currency FROM online_sale_settlements WHERE settlement_id=?",new String[]{id})){if(!c.moveToFirst())throw new IllegalStateException("تسوية بيع الإنترنت غير موجودة قبل التحصيل");due=c.getDouble(0);base=c.getString(1);}
  double amount=j.optDouble("collectionAmount");if(amount<=0)throw new IllegalArgumentException("مبلغ تحصيل بيع الإنترنت غير صالح");String cur=j.optString("currency",base);if(!base.equals(cur))throw new IllegalArgumentException("عملة تحصيل بيع الإنترنت مختلفة عن التسوية");double paid=j.optDouble("paidTotal");double remaining=j.has("remaining")?j.optDouble("remaining"):Math.max(0,due-paid);if(paid<0||paid>due||remaining<0)throw new IllegalArgumentException("إجمالي تحصيل بيع الإنترنت غير صالح");long now=j.optLong("collectedAt",j.optLong("updatedAt",System.currentTimeMillis()));
  ContentValues v=new ContentValues();v.put("paid_total",paid);v.put("remaining",remaining);v.put("updated_at",now);if(db.update("online_sale_settlements",v,"settlement_id=?",new String[]{id})!=1)throw new IllegalStateException("تعذر تحديث تسوية بيع الإنترنت");payment(db,"remote-online-collection-"+op.id,id,amount,cur,j.optString("paymentMethod","CASH"),"COLLECTION",now);
 }
 private static void payment(SQLiteDatabase db,String paymentId,String settlementId,double amount,String currency,String method,String type,long now){ContentValues p=new ContentValues();p.put("payment_id",paymentId);p.put("settlement_id",settlementId);p.put("amount",amount);p.put("currency",currency);p.put("payment_method",method);p.put("payment_type",type);p.put("created_at",now);db.insertWithOnConflict("online_sale_payments",null,p,SQLiteDatabase.CONFLICT_IGNORE);}
}
