package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.alsafa.native4.sync.SyncOperation;
import org.json.JSONObject;
import java.util.UUID;

/** Direct counter sale of a repair part, without creating a repair intake/order. */
public final class DirectRepairPartSaleStore {
 private final CoreStore.DbProvider provider; private final String deviceId;
 public DirectRepairPartSaleStore(CoreStore.DbProvider provider,String deviceId){this.provider=provider;this.deviceId=deviceId;}
 public Result sell(String partId,int quantity,double unitPrice,double serviceFee,String currency,String paymentMethod)throws Exception{
  String pid=req(partId,"قطعة الصيانة"),cur=currency(currency),pay=req(paymentMethod,"طريقة الدفع");
  if(quantity<=0)throw new IllegalArgumentException("الكمية يجب أن تكون أكبر من صفر");
  if(!Double.isFinite(unitPrice)||unitPrice<0||!Double.isFinite(serviceFee)||serviceFee<0)throw new IllegalArgumentException("السعر أو أجرة الخدمة غير صالحة");
  SQLiteDatabase db=provider.db();db.beginTransaction();try{
   String name;int before;double cost;
   try(Cursor c=db.rawQuery("SELECT name,quantity,cost FROM repair_part_catalog WHERE part_id=? AND deleted_at IS NULL",new String[]{pid})){
    if(!c.moveToFirst())throw new IllegalArgumentException("قطعة الصيانة غير موجودة");name=c.getString(0);before=c.getInt(1);cost=c.getDouble(2);
   }
   if(before<quantity)throw new IllegalArgumentException("رصيد قطعة الصيانة غير كافٍ");
   int after=before-quantity;double partsTotal=unitPrice*quantity,total=partsTotal+serviceFee;long now=System.currentTimeMillis();String saleId="partsale-"+UUID.randomUUID();
   ContentValues p=new ContentValues();p.put("quantity",after);p.put("updated_at",now);db.update("repair_part_catalog",p,"part_id=?",new String[]{pid});
   JSONObject j=new JSONObject().put("saleId",saleId).put("saleType","REPAIR_PART_DIRECT").put("partId",pid).put("partName",name).put("quantity",quantity).put("beforeQty",before).put("afterQty",after).put("unitCost",cost).put("unitPrice",unitPrice).put("partsTotal",partsTotal).put("serviceFee",serviceFee).put("total",total).put("currency",cur).put("paymentMethod",pay).put("createdAt",now);
   provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.SALE_CREATE,saleId,j.toString(),deviceId));
   db.setTransactionSuccessful();return new Result(saleId,name,before,after,total,cur,pay);
  }finally{db.endTransaction();}
 }
 private static String req(String v,String l){String s=trim(v);if(s.isEmpty())throw new IllegalArgumentException(l+" مطلوب");return s;}private static String trim(String v){return v==null?"":v.trim();}private static String currency(String v){String x=trim(v).toUpperCase();return x.equals("TRY")||x.equals("SYP")?x:"USD";}
 public static final class Result{public final String saleId,partName,currency,paymentMethod;public final int beforeQty,remainingQty;public final double total;Result(String saleId,String partName,int beforeQty,int remainingQty,double total,String currency,String paymentMethod){this.saleId=saleId;this.partName=partName;this.beforeQty=beforeQty;this.remainingQty=remainingQty;this.total=total;this.currency=currency;this.paymentMethod=paymentMethod;}}
}
