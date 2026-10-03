package com.alsafa.native4.core;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Locale;

/** Read-only reports from committed accounting data. USD totals use immutable transaction FX snapshots. */
public final class ReportStore {
 private final CoreStore.DbProvider provider;public ReportStore(CoreStore.DbProvider provider){this.provider=provider;}
 public Summary day(String yyyyMmDd){long a=parse(yyyyMmDd+" 00:00:00"),b=parse(yyyyMmDd+" 23:59:59");return query(a,b);}
 public Summary month(String yyyyMm){return prefix(yyyyMm);}
 public Summary year(String yyyy){return prefix(yyyy);}
 private Summary prefix(String p){long[] r=bounds(p);return query(r[0],r[1]);}
 private Summary query(long from,long to){SQLiteDatabase db=provider.db();int saleCount=count(db,"SELECT COUNT(*) FROM invoices WHERE invoice_type='SALE' AND deleted_at IS NULL AND created_at BETWEEN ? AND ?",from,to),repairCount=count(db,"SELECT COUNT(*) FROM repairs WHERE deleted_at IS NULL AND received_at BETWEEN ? AND ?",from,to);double salesUsd=sumFx(db,"SALE",from,to),repairsUsd=sumFx(db,"REPAIR",from,to),directPartsUsd=sumFx(db,"REPAIR_PART_DIRECT",from,to);double saleProfitUsd=saleProfitUsd(db,from,to);double repairPartsCostUsd=repairPartsCostUsd(db,from,to);double repairProfitUsd=repairsUsd+directPartsUsd-repairPartsCostUsd;double totalProfitUsd=saleProfitUsd+repairProfitUsd;double purchasesUsd=purchasesUsd(db,from,to);return new Summary(saleCount,repairCount,salesUsd,repairsUsd,directPartsUsd,purchasesUsd,saleProfitUsd,repairProfitUsd,totalProfitUsd);}
 private static double sumFx(SQLiteDatabase db,String type,long from,long to){try(Cursor c=db.rawQuery("SELECT COALESCE(SUM(usd_amount),0) FROM fx_snapshots WHERE source_type=? AND captured_at BETWEEN ? AND ?",new String[]{type,String.valueOf(from),String.valueOf(to)})){return c.moveToFirst()?c.getDouble(0):0;}}
 private static double saleProfitUsd(SQLiteDatabase db,long from,long to){try(Cursor c=db.rawQuery("SELECT COALESCE(SUM(i.profit_snapshot * f.usd_per_unit),0) FROM invoice_items i JOIN invoices v ON v.invoice_id=i.invoice_id JOIN fx_snapshots f ON f.source_type='SALE' AND f.source_id=v.invoice_id WHERE v.deleted_at IS NULL AND v.created_at BETWEEN ? AND ?",new String[]{String.valueOf(from),String.valueOf(to)})){return c.moveToFirst()?c.getDouble(0):0;}}
 private static double repairPartsCostUsd(SQLiteDatabase db,long from,long to){try(Cursor c=db.rawQuery("SELECT COALESCE(SUM(rp.unit_cost*rp.quantity*f.usd_per_unit),0) FROM repair_parts rp JOIN repairs r ON r.repair_id=rp.repair_id JOIN fx_snapshots f ON f.source_type='REPAIR' AND f.source_id=r.repair_id WHERE r.deleted_at IS NULL AND r.received_at BETWEEN ? AND ?",new String[]{String.valueOf(from),String.valueOf(to)})){return c.moveToFirst()?c.getDouble(0):0;}}
 private static double purchasesUsd(SQLiteDatabase db,long from,long to){try(Cursor c=db.rawQuery("SELECT COALESCE(SUM(total),0) FROM invoices WHERE invoice_type='PURCHASE' AND currency='USD' AND deleted_at IS NULL AND created_at BETWEEN ? AND ?",new String[]{String.valueOf(from),String.valueOf(to)})){return c.moveToFirst()?c.getDouble(0):0;}}
 private static int count(SQLiteDatabase db,String sql,long a,long b){try(Cursor c=db.rawQuery(sql,new String[]{String.valueOf(a),String.valueOf(b)})){return c.moveToFirst()?c.getInt(0):0;}}
 private static long[] bounds(String p){try{java.text.SimpleDateFormat f=p.length()==7?new java.text.SimpleDateFormat("yyyy-MM",Locale.US):new java.text.SimpleDateFormat("yyyy",Locale.US);java.util.Date d=f.parse(p);java.util.Calendar c=java.util.Calendar.getInstance();c.setTime(d);long a=c.getTimeInMillis();if(p.length()==7)c.add(java.util.Calendar.MONTH,1);else c.add(java.util.Calendar.YEAR,1);return new long[]{a,c.getTimeInMillis()-1};}catch(Exception e){throw new IllegalArgumentException("التاريخ غير صالح");}}
 private static long parse(String s){try{return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).parse(s).getTime();}catch(Exception e){throw new IllegalArgumentException("التاريخ غير صالح");}}
 public static final class Summary{public final int salesCount,repairsCount;public final double salesUsd,repairsUsd,directPartsUsd,purchasesUsd,saleProfitUsd,repairProfitUsd,totalProfitUsd;Summary(int s,int r,double su,double ru,double du,double pu,double sp,double rp,double tp){salesCount=s;repairsCount=r;salesUsd=su;repairsUsd=ru;directPartsUsd=du;purchasesUsd=pu;saleProfitUsd=sp;repairProfitUsd=rp;totalProfitUsd=tp;}}
}
