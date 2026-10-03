package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.UUID;

/** Writes immutable FX snapshots inside the caller's existing SQLite transaction. */
public final class FxSnapshotStore {
 private FxSnapshotStore(){}
 public static Snapshot capture(SQLiteDatabase db,String sourceType,String sourceId,String currency,double amount,long capturedAt){String cur=currency(currency);if(!Double.isFinite(amount)||amount<0)throw new IllegalArgumentException("مبلغ لقطة الصرف غير صالح");double usdPerUnit=rateForTime(db,cur,capturedAt);double usd=amount*usdPerUnit;ContentValues v=new ContentValues();v.put("snapshot_id","fx-"+UUID.randomUUID());v.put("source_type",sourceType);v.put("source_id",sourceId);v.put("currency",cur);v.put("original_amount",amount);v.put("usd_per_unit",usdPerUnit);v.put("usd_amount",usd);v.put("captured_at",capturedAt);long r=db.insertWithOnConflict("fx_snapshots",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(r==-1){try(Cursor c=db.rawQuery("SELECT currency,original_amount,usd_per_unit,usd_amount,captured_at FROM fx_snapshots WHERE source_type=? AND source_id=? LIMIT 1",new String[]{sourceType,sourceId})){if(!c.moveToFirst())throw new IllegalStateException("تعذر حفظ لقطة سعر الصرف");return new Snapshot(c.getString(0),c.getDouble(1),c.getDouble(2),c.getDouble(3),c.getLong(4));}}return new Snapshot(cur,amount,usdPerUnit,usd,capturedAt);}
 private static double rateForTime(SQLiteDatabase db,String cur,long at){if("USD".equals(cur))return 1.0;try(Cursor c=db.rawQuery("SELECT d.business_day,f.try_per_usd,f.syp_per_usd FROM work_days d JOIN daily_fx_rates f ON f.business_day=d.business_day WHERE d.opened_at<=? AND (d.closed_at IS NULL OR d.closed_at>=?) ORDER BY d.opened_at DESC LIMIT 1",new String[]{String.valueOf(at),String.valueOf(at)})){if(!c.moveToFirst())throw new IllegalStateException("لا يوجد سعر صرف معتمد ليوم العمل الحالي");double perUsd="TRY".equals(cur)?c.getDouble(1):c.getDouble(2);if(!Double.isFinite(perUsd)||perUsd<=0)throw new IllegalStateException("سعر صرف يوم العمل غير صالح");return 1.0/perUsd;}}
 private static String currency(String v){String x=v==null?"":v.trim().toUpperCase();if(!x.equals("USD")&&!x.equals("TRY")&&!x.equals("SYP"))throw new IllegalArgumentException("عملة غير مدعومة");return x;}
 public static final class Snapshot{public final String currency;public final double originalAmount,usdPerUnit,usdAmount;public final long capturedAt;Snapshot(String c,double a,double r,double u,long t){currency=c;originalAmount=a;usdPerUnit=r;usdAmount=u;capturedAt=t;}}
}
