package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.alsafa.native4.sync.SyncOperation;
import org.json.JSONObject;
import java.util.UUID;

/** Work-day opening and immutable closing. Expected = opening carry + MAIN movements inside this work day only. */
public final class DailyClosingStore {
 private final CoreStore.DbProvider provider; private final String deviceId;
 public DailyClosingStore(CoreStore.DbProvider provider,String deviceId){this.provider=provider;this.deviceId=deviceId;}

 public void open(String businessDay,double openingUsd,double openingTry,double openingSyp,String sourceClosingId)throws Exception{
  String day=day(businessDay);valid(openingUsd);valid(openingTry);valid(openingSyp);SQLiteDatabase db=provider.db();db.beginTransaction();
  try{
   if(hasOpenDay(db))throw new IllegalStateException("يوجد يوم عمل مفتوح بالفعل");
   if(workDayExists(db,day))throw new IllegalStateException("تم فتح يوم العمل هذا مسبقاً");
   long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("business_day",day);v.put("status","OPEN");v.put("opened_at",now);v.put("opening_usd",openingUsd);v.put("opening_try",openingTry);v.put("opening_syp",openingSyp);v.put("source_closing_id",trim(sourceClosingId));db.insertOrThrow("work_days",null,v);
   JSONObject p=new JSONObject().put("businessDay",day).put("openedAt",now).put("openingUSD",openingUsd).put("openingTRY",openingTry).put("openingSYP",openingSyp).put("sourceClosingId",trim(sourceClosingId));
   provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.WORK_DAY_OPEN,day,p.toString(),deviceId));db.setTransactionSuccessful();
  }finally{db.endTransaction();}
 }

 public Expected expected(String businessDay){String day=day(businessDay);SQLiteDatabase db=provider.db();WorkDay w=workDay(db,day,true);long now=System.currentTimeMillis();return expectedAt(db,w,now);}

 /** Compatibility path: when no explicit carry is supplied, all counted cash is carried to the next day. */
 public String close(String businessDay,double countedUsd,double countedTry,double countedSyp,String notes)throws Exception{
  return close(businessDay,countedUsd,countedTry,countedSyp,countedUsd,countedTry,countedSyp,notes);
 }

 /** Immutable close. carry = physical cash intentionally left in MAIN for the next work day. */
 public String close(String businessDay,double countedUsd,double countedTry,double countedSyp,double carryUsd,double carryTry,double carrySyp,String notes)throws Exception{
  String day=day(businessDay);valid(countedUsd);valid(countedTry);valid(countedSyp);validCarry(countedUsd,carryUsd);validCarry(countedTry,carryTry);validCarry(countedSyp,carrySyp);SQLiteDatabase db=provider.db();db.beginTransaction();
  try{
   if(closingExists(db,day))throw new IllegalStateException("هذا اليوم مقفل مسبقاً");
   WorkDay w=workDay(db,day,true);long now=System.currentTimeMillis();Expected e=expectedAt(db,w,now);String id="closing-"+UUID.randomUUID();double outUsd=drawerOut(countedUsd,carryUsd),outTry=drawerOut(countedTry,carryTry),outSyp=drawerOut(countedSyp,carrySyp);
   ContentValues v=new ContentValues();v.put("closing_id",id);v.put("business_day",day);v.put("expected_usd",e.usd);v.put("counted_usd",countedUsd);v.put("carry_usd",carryUsd);v.put("drawer_out_usd",outUsd);v.put("expected_try",e.tryAmount);v.put("counted_try",countedTry);v.put("carry_try",carryTry);v.put("drawer_out_try",outTry);v.put("expected_syp",e.syp);v.put("counted_syp",countedSyp);v.put("carry_syp",carrySyp);v.put("drawer_out_syp",outSyp);v.put("closed_at",now);v.put("notes",trim(notes));db.insertOrThrow("daily_closings",null,v);
   ContentValues done=new ContentValues();done.put("status","CLOSED");done.put("closing_id",id);done.put("closed_at",now);if(db.update("work_days",done,"business_day=? AND status='OPEN'",new String[]{day})!=1)throw new IllegalStateException("تعذر تثبيت إقفال يوم العمل");
   JSONObject p=new JSONObject().put("closingId",id).put("businessDay",day).put("openedAt",w.openedAt).put("closedAt",now).put("openingUSD",w.openingUsd).put("movementUSD",e.usd-w.openingUsd).put("expectedUSD",e.usd).put("countedUSD",countedUsd).put("differenceUSD",countedUsd-e.usd).put("carryUSD",carryUsd).put("drawerOutUSD",outUsd).put("openingTRY",w.openingTry).put("movementTRY",e.tryAmount-w.openingTry).put("expectedTRY",e.tryAmount).put("countedTRY",countedTry).put("differenceTRY",countedTry-e.tryAmount).put("carryTRY",carryTry).put("drawerOutTRY",outTry).put("openingSYP",w.openingSyp).put("movementSYP",e.syp-w.openingSyp).put("expectedSYP",e.syp).put("countedSYP",countedSyp).put("differenceSYP",countedSyp-e.syp).put("carrySYP",carrySyp).put("drawerOutSYP",outSyp);
   provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.DAILY_CLOSING,id,p.toString(),deviceId));db.setTransactionSuccessful();return id;
  }finally{db.endTransaction();}
 }

 static double calculateExpected(double opening,double incoming,double outgoing){return opening+incoming-outgoing;}
 static double drawerOut(double counted,double carry){validCarry(counted,carry);return counted-carry;}

 private static Expected expectedAt(SQLiteDatabase db,WorkDay w,long end){
  return new Expected(calculateExpected(w.openingUsd,movements(db,w.openedAt,end,"USD","IN"),movements(db,w.openedAt,end,"USD","OUT")),calculateExpected(w.openingTry,movements(db,w.openedAt,end,"TRY","IN"),movements(db,w.openedAt,end,"TRY","OUT")),calculateExpected(w.openingSyp,movements(db,w.openedAt,end,"SYP","IN"),movements(db,w.openedAt,end,"SYP","OUT")));
 }
 private static double movements(SQLiteDatabase db,long start,long end,String currency,String direction){try(Cursor c=db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM cash_moves WHERE drawer='MAIN' AND currency=? AND direction=? AND created_at>=? AND created_at<=?",new String[]{currency,direction,String.valueOf(start),String.valueOf(end)})){return c.moveToFirst()?c.getDouble(0):0;}}
 private static WorkDay workDay(SQLiteDatabase db,String day,boolean requireOpen){try(Cursor c=db.rawQuery("SELECT status,opened_at,opening_usd,opening_try,opening_syp FROM work_days WHERE business_day=? LIMIT 1",new String[]{day})){if(!c.moveToFirst())throw new IllegalStateException("يجب فتح يوم العمل وتحديد المدوّر أولاً");if(requireOpen&&!"OPEN".equals(c.getString(0)))throw new IllegalStateException("يوم العمل ليس مفتوحاً");return new WorkDay(c.getLong(1),c.getDouble(2),c.getDouble(3),c.getDouble(4));}}
 private static boolean hasOpenDay(SQLiteDatabase db){try(Cursor c=db.rawQuery("SELECT 1 FROM work_days WHERE status='OPEN' LIMIT 1",null)){return c.moveToFirst();}}
 private static boolean workDayExists(SQLiteDatabase db,String day){try(Cursor c=db.rawQuery("SELECT 1 FROM work_days WHERE business_day=? LIMIT 1",new String[]{day})){return c.moveToFirst();}}
 private static boolean closingExists(SQLiteDatabase db,String day){try(Cursor c=db.rawQuery("SELECT 1 FROM daily_closings WHERE business_day=? LIMIT 1",new String[]{day})){return c.moveToFirst();}}
 private static String day(String value){String v=trim(value);if(!v.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException("تاريخ يوم العمل غير صالح");return v;}
 private static void valid(double v){if(v<0||!Double.isFinite(v))throw new IllegalArgumentException("المبلغ غير صالح");}
 private static void validCarry(double counted,double carry){valid(counted);valid(carry);if(carry>counted)throw new IllegalArgumentException("المدوّر لا يمكن أن يكون أكبر من المبلغ المعدود");}
 private static String trim(String v){return v==null?"":v.trim();}
 private static final class WorkDay{final long openedAt;final double openingUsd,openingTry,openingSyp;WorkDay(long openedAt,double openingUsd,double openingTry,double openingSyp){this.openedAt=openedAt;this.openingUsd=openingUsd;this.openingTry=openingTry;this.openingSyp=openingSyp;}}
 public static final class Expected{public final double usd,tryAmount,syp;Expected(double usd,double tryAmount,double syp){this.usd=usd;this.tryAmount=tryAmount;this.syp=syp;}}
}
