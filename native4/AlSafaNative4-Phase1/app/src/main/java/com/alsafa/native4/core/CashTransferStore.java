package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import com.alsafa.native4.sync.SyncOperation;
import org.json.JSONObject;
import java.util.UUID;

/** Drawer/vault/bank/external transfers and owner withdrawals; currencies never mix. */
public final class CashTransferStore {
 private final CoreStore.DbProvider provider;private final String deviceId;
 public CashTransferStore(CoreStore.DbProvider provider,String deviceId){this.provider=provider;this.deviceId=deviceId;}
 public String transfer(String from,String to,double amount,String currency,String notes)throws Exception{
  String f=req(from,"الصندوق المصدر"),t=req(to,"الصندوق الهدف"),c=currency(currency);if(f.equals(t))throw new IllegalArgumentException("المصدر والهدف متطابقان");if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("المبلغ غير صالح");long now=System.currentTimeMillis();String id="transfer-"+UUID.randomUUID();SQLiteDatabase db=provider.db();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("transfer_id",id);v.put("from_drawer",f);v.put("to_drawer",t);v.put("amount",amount);v.put("currency",c);v.put("notes",trim(notes));v.put("created_at",now);db.insertOrThrow("cash_transfers",null,v);cash(db,id,"OUT",amount,c,f,now);cash(db,id,"IN",amount,c,t,now);JSONObject j=new JSONObject().put("transferId",id).put("from",f).put("to",t).put("amount",amount).put("currency",c).put("notes",trim(notes)).put("createdAt",now);provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.SETTING,id,j.toString(),deviceId));db.setTransactionSuccessful();return id;}finally{db.endTransaction();}}
 public String withdraw(String owner,double amount,String currency,String sourceDrawer,String notes)throws Exception{
  String o=req(owner,"صاحب السحب"),d=req(sourceDrawer,"الصندوق"),c=currency(currency);if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("المبلغ غير صالح");long now=System.currentTimeMillis();String id="withdrawal-"+UUID.randomUUID();SQLiteDatabase db=provider.db();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("withdrawal_id",id);v.put("owner",o);v.put("amount",amount);v.put("currency",c);v.put("source_drawer",d);v.put("notes",trim(notes));v.put("created_at",now);db.insertOrThrow("withdrawals",null,v);cash(db,id,"OUT",amount,c,d,now);JSONObject j=new JSONObject().put("withdrawalId",id).put("owner",o).put("amount",amount).put("currency",c).put("sourceDrawer",d).put("notes",trim(notes)).put("createdAt",now);provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.SETTING,id,j.toString(),deviceId));db.setTransactionSuccessful();return id;}finally{db.endTransaction();}}
 private static void cash(SQLiteDatabase db,String source,String direction,double amount,String currency,String drawer,long now){ContentValues c=new ContentValues();c.put("move_id","cash-"+UUID.randomUUID());c.put("source_type","TRANSFER");c.put("source_id",source);c.put("direction",direction);c.put("amount",amount);c.put("currency",currency);c.put("payment_method","CASH");c.put("drawer",drawer);c.put("created_at",now);db.insertOrThrow("cash_moves",null,c);}
 private static String currency(String v){String x=trim(v).toUpperCase();if(!x.equals("USD")&&!x.equals("TRY")&&!x.equals("SYP"))throw new IllegalArgumentException("العملة غير مدعومة");return x;}private static String req(String v,String label){String s=trim(v);if(s.isEmpty())throw new IllegalArgumentException(label+" مطلوب");return s;}private static String trim(String v){return v==null?"":v.trim();}
}
