package com.alsafa.native4.core;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Locale;

/** Read-only reports from local committed accounting data; never mutates stock or sync state. */
public final class ReportStore {
 private final CoreStore.DbProvider provider;public ReportStore(CoreStore.DbProvider provider){this.provider=provider;}
 public Summary day(String yyyyMmDd){return range(yyyyMmDd+" 00:00:00",yyyyMmDd+" 23:59:59");}
 public Summary month(String yyyyMm){return prefix(yyyyMm);}
 public Summary year(String yyyy){return prefix(yyyy);}
 private Summary prefix(String p){SQLiteDatabase db=provider.db();return query(db,"strftime('%Y-%m-%d',created_at/1000,'unixepoch') LIKE ?",new String[]{p+"%"});}
 private Summary range(String from,String to){SQLiteDatabase db=provider.db();long a=parse(from),b=parse(to);return query(db,"created_at BETWEEN ? AND ?",new String[]{String.valueOf(a),String.valueOf(b)});}
 private Summary query(SQLiteDatabase db,String where,String[] args){double sales=0,purchases=0;int saleCount=0,repairCount=0;try(Cursor c=db.rawQuery("SELECT invoice_type,COUNT(*),COALESCE(SUM(total),0) FROM invoices WHERE deleted_at IS NULL AND "+where+" GROUP BY invoice_type",args)){while(c.moveToNext()){String t=c.getString(0);if("SALE".equals(t)){saleCount=c.getInt(1);sales=c.getDouble(2);}else if("PURCHASE".equals(t))purchases=c.getDouble(2);}}try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM repairs WHERE deleted_at IS NULL AND "+where,args)){if(c.moveToFirst())repairCount=c.getInt(0);}return new Summary(saleCount,repairCount,sales,purchases);}
 private static long parse(String s){try{return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).parse(s).getTime();}catch(Exception e){throw new IllegalArgumentException("التاريخ غير صالح");}}
 public static final class Summary{public final int salesCount,repairsCount;public final double salesTotal,purchasesTotal;Summary(int s,int r,double st,double pt){salesCount=s;repairsCount=r;salesTotal=st;purchasesTotal=pt;}}
}
