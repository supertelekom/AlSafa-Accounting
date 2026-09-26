package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.alsafa.native4.sync.SyncOperation;
import org.json.JSONObject;
import java.util.UUID;

/** Daily closing records counted vs ledger-expected amounts per currency; it never mutates historical movements. */
public final class DailyClosingStore {
 private final CoreStore.DbProvider provider; private final String deviceId;
 public DailyClosingStore(CoreStore.DbProvider provider,String deviceId){this.provider=provider;this.deviceId=deviceId;}
 public String close(String businessDay,double countedUsd,double countedTry,double countedSyp,String notes)throws Exception{String day=businessDay==null?"":businessDay.trim();if(!day.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException("تاريخ يوم العمل غير صالح");valid(countedUsd);valid(countedTry);valid(countedSyp);SQLiteDatabase db=provider.db();db.beginTransaction();try{if(exists(db,day))throw new IllegalStateException("هذا اليوم مقفل مسبقاً");double usd=balance(db,"MAIN","USD"),tr=balance(db,"MAIN","TRY"),syp=balance(db,"MAIN","SYP");long now=System.currentTimeMillis();String id="closing-"+UUID.randomUUID();ContentValues v=new ContentValues();v.put("closing_id",id);v.put("business_day",day);v.put("expected_usd",usd);v.put("counted_usd",countedUsd);v.put("expected_try",tr);v.put("counted_try",countedTry);v.put("expected_syp",syp);v.put("counted_syp",countedSyp);v.put("closed_at",now);v.put("notes",notes==null?"":notes.trim());db.insertOrThrow("daily_closings",null,v);JSONObject p=new JSONObject().put("closingId",id).put("businessDay",day).put("expectedUSD",usd).put("countedUSD",countedUsd).put("differenceUSD",countedUsd-usd).put("expectedTRY",tr).put("countedTRY",countedTry).put("differenceTRY",countedTry-tr).put("expectedSYP",syp).put("countedSYP",countedSyp).put("differenceSYP",countedSyp-syp).put("closedAt",now);provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.DAILY_CLOSING,id,p.toString(),deviceId));db.setTransactionSuccessful();return id;}finally{db.endTransaction();}}
 private static boolean exists(SQLiteDatabase db,String day){try(Cursor c=db.rawQuery("SELECT 1 FROM daily_closings WHERE business_day=? LIMIT 1",new String[]{day})){return c.moveToFirst();}}
 private static double balance(SQLiteDatabase db,String drawer,String currency){try(Cursor c=db.rawQuery("SELECT COALESCE(SUM(CASE WHEN direction='IN' THEN amount ELSE -amount END),0) FROM cash_moves WHERE drawer=? AND currency=?",new String[]{drawer,currency})){return c.moveToFirst()?c.getDouble(0):0;}}
 private static void valid(double v){if(v<0||!Double.isFinite(v))throw new IllegalArgumentException("المبلغ المعدود غير صالح");}
}
