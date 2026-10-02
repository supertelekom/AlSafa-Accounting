package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import com.alsafa.native4.sync.SyncOperation;
import org.json.JSONObject;
import java.util.UUID;

/** Atomic repair intake: repair + optional cash receipt + durable sync command. */
public final class RepairStore {
 private final CoreStore.DbProvider provider; private final String deviceId;
 public RepairStore(CoreStore.DbProvider provider,String deviceId){this.provider=provider;this.deviceId=deviceId;}
 public String receive(String customer,String phone,String watch,double total,String currency,String payment,boolean leftWithStore)throws Exception{
  String c=required(customer,"اسم العميل"),w=required(watch,"وصف الساعة");if(total<0||!Double.isFinite(total))throw new IllegalArgumentException("قيمة الصيانة غير صالحة");long now=System.currentTimeMillis();String id="repair-"+UUID.randomUUID(),invoice="R-"+now,status=leftWithStore?"ترك الساعة لدينا":"تم التسليم",pay=payment(payment),cur=currency(currency);String cashMoveId=(total>0&&!"LATER".equals(pay))?"repair-cash-"+id:null;SQLiteDatabase db=provider.db();db.beginTransaction();try{ContentValues r=new ContentValues();r.put("repair_id",id);r.put("invoice_no",invoice);r.put("customer_name",c);r.put("customer_phone",trim(phone));r.put("watch_description",w);r.put("status",status);r.put("total",total);r.put("currency",cur);r.put("payment_method",pay);r.put("received_at",now);r.put("updated_at",now);if(!leftWithStore)r.put("delivered_at",now);db.insertOrThrow("repairs",null,r);if(cashMoveId!=null){ContentValues cash=new ContentValues();cash.put("move_id",cashMoveId);cash.put("source_type","REPAIR");cash.put("source_id",id);cash.put("direction","IN");cash.put("amount",total);cash.put("currency",cur);cash.put("payment_method",pay);cash.put("created_at",now);db.insertOrThrow("cash_moves",null,cash);}JSONObject payload=new JSONObject().put("repairId",id).put("invoiceNo",invoice).put("customerName",c).put("customerPhone",trim(phone)).put("watch",w).put("status",status).put("total",total).put("currency",cur).put("paymentMethod",pay).put("receivedAt",now).put("updatedAt",now).put("cashReceived",cashMoveId!=null);if(cashMoveId!=null)payload.put("cashMoveId",cashMoveId).put("cashDirection","IN").put("cashAmount",total).put("cashCurrency",cur).put("cashPaymentMethod",pay).put("cashCreatedAt",now);provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.REPAIR,id,payload.toString(),deviceId));db.setTransactionSuccessful();return invoice;}finally{db.endTransaction();}}
 private static String required(String v,String label){String s=trim(v);if(s.isEmpty()||s.length()>160)throw new IllegalArgumentException(label+" غير صالح");return s;}private static String trim(String v){return v==null?"":v.trim();}private static String currency(String v){String x=trim(v).toUpperCase();return x.equals("TRY")||x.equals("SYP")?x:"USD";}private static String payment(String v){String x=trim(v).toUpperCase();return x.equals("SHAM_CASH")||x.equals("EXTERNAL")||x.equals("LATER")?x:"CASH";}
}
