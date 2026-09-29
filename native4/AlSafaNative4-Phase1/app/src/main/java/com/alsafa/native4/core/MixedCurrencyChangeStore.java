package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import com.alsafa.native4.sync.SyncOperation;
import org.json.JSONObject;
import java.util.UUID;

/** Records customer change/refund paid from a different drawer currency without pretending it is a discount. */
public final class MixedCurrencyChangeStore {
 private final CoreStore.DbProvider provider;private final String deviceId;
 public MixedCurrencyChangeStore(CoreStore.DbProvider provider,String deviceId){this.provider=provider;this.deviceId=deviceId;}
 public Result record(String invoiceId,double saleCurrencyValue,String saleCurrency,double paidOutAmount,String paidOutCurrency,double rateSalePerPaidOut,String drawer)throws Exception{
  if(trim(invoiceId).isEmpty())throw new IllegalArgumentException("مرجع الفاتورة مطلوب");if(saleCurrencyValue<=0||paidOutAmount<=0||rateSalePerPaidOut<=0)throw new IllegalArgumentException("قيم الباقي أو سعر الصرف غير صحيحة");
  String sc=currency(saleCurrency),pc=currency(paidOutCurrency);if(sc.equals(pc))throw new IllegalArgumentException("استخدم حركة باقي عادية عندما تكون العملة نفسها");double equivalent=paidOutAmount*rateSalePerPaidOut;double diff=equivalent-saleCurrencyValue;long now=System.currentTimeMillis();String id="change-"+UUID.randomUUID();SQLiteDatabase db=provider.db();db.beginTransaction();try{ContentValues cm=new ContentValues();cm.put("move_id",id);cm.put("source_type","SALE_CHANGE_FX");cm.put("source_id",invoiceId);cm.put("direction","OUT");cm.put("amount",paidOutAmount);cm.put("currency",pc);cm.put("payment_method","CASH");cm.put("drawer",trim(drawer).isEmpty()?"MAIN":drawer);cm.put("created_at",now);db.insertOrThrow("cash_moves",null,cm);JSONObject j=new JSONObject().put("changeId",id).put("invoiceId",invoiceId).put("saleCurrencyValue",saleCurrencyValue).put("saleCurrency",sc).put("paidOutAmount",paidOutAmount).put("paidOutCurrency",pc).put("rateSalePerPaidOut",rateSalePerPaidOut).put("equivalentSaleCurrency",equivalent).put("roundingDifference",diff).put("drawer",trim(drawer).isEmpty()?"MAIN":drawer).put("updatedAt",now);provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.CASH_MOVEMENT,id,j.toString(),deviceId));db.setTransactionSuccessful();return new Result(id,equivalent,diff);}finally{db.endTransaction();}
 }
 public static final class Result{public final String id;public final double equivalent,roundingDifference;Result(String i,double e,double d){id=i;equivalent=e;roundingDifference=d;}}
 private static String currency(String v){String x=trim(v).toUpperCase();return x.equals("TRY")||x.equals("SYP")?x:"USD";}private static String trim(String v){return v==null?"":v.trim();}
}
