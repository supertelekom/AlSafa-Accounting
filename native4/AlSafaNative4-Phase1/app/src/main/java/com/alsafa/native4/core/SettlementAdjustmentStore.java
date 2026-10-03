package com.alsafa.native4.core;

import android.content.ContentValues;import android.database.sqlite.SQLiteDatabase;import java.util.UUID;
/** Records explicit user-confirmed difference between agreed amount and amount finally collected. */
public final class SettlementAdjustmentStore {
 private SettlementAdjustmentStore(){}
 public static Result requireAndWrite(SQLiteDatabase db,String sourceType,String sourceId,double agreed,double collected,String currency,boolean confirmed,String reason,long now){String cur=MoneyRules.currency(currency);if(!Double.isFinite(agreed)||agreed<0||!Double.isFinite(collected)||collected<0)throw new IllegalArgumentException("المبلغ غير صالح");double delta=collected-agreed;if(Math.abs(delta)<0.000001)return new Result(false,0,null);if(!confirmed)throw new ConfirmationRequiredException(agreed,collected,cur);String id="adjustment-"+UUID.randomUUID();ContentValues v=new ContentValues();v.put("adjustment_id",id);v.put("source_type",sourceType);v.put("source_id",sourceId);v.put("agreed_amount",agreed);v.put("collected_amount",collected);v.put("currency",cur);v.put("difference",delta);v.put("reason",reason==null?"":reason.trim());v.put("created_at",now);db.insertOrThrow("settlement_adjustments",null,v);return new Result(true,delta,id);}
 public static final class ConfirmationRequiredException extends IllegalStateException{public final double agreed,collected;public final String currency;ConfirmationRequiredException(double a,double c,String cur){super("المبلغ المسجل "+a+" "+cur+"، والمبلغ المراد تحصيله "+c+" "+cur+". يلزم تأكيد الفرق قبل الحفظ.");agreed=a;collected=c;currency=cur;}}
 public static final class Result{public final boolean adjusted;public final double difference;public final String adjustmentId;Result(boolean a,double d,String id){adjusted=a;difference=d;adjustmentId=id;}}
}
