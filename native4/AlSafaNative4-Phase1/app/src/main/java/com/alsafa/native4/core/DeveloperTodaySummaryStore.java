package com.alsafa.native4.core;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Fast local-only dashboard totals. Sales and repairs are deliberately separate by currency. */
public final class DeveloperTodaySummaryStore {
 private final CoreStore.DbProvider provider;
 public DeveloperTodaySummaryStore(CoreStore.DbProvider provider){this.provider=provider;}
 public Summary read(String day){if(day==null||!day.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException("تاريخ اليوم غير صالح");SQLiteDatabase db=provider.db();long from=atStart(day),to=from+86400000L;Summary s=new Summary();readSales(db,from,to,s.sales);readRepairs(db,from,to,s.repairs);readSaleProfit(db,from,to,s.saleProfit);readRepairProfit(db,from,to,s.repairProfit);readCarry(db,day,s.carry);return s;}
 private static void readSales(SQLiteDatabase db,long from,long to,Money out){try(Cursor c=db.rawQuery("SELECT currency,COALESCE(SUM(total),0) FROM invoices WHERE invoice_type='SALE' AND created_at>=? AND created_at<? GROUP BY currency",new String[]{String.valueOf(from),String.valueOf(to)})){while(c.moveToNext())out.set(c.getString(0),c.getDouble(1));}}
 private static void readRepairs(SQLiteDatabase db,long from,long to,Money out){try(Cursor c=db.rawQuery("SELECT currency,COALESCE(SUM(total),0) FROM repairs WHERE deleted_at IS NULL AND received_at>=? AND received_at<? GROUP BY currency",new String[]{String.valueOf(from),String.valueOf(to)})){while(c.moveToNext())out.set(c.getString(0),c.getDouble(1));}}
 private static void readSaleProfit(SQLiteDatabase db,long from,long to,Money out){try(Cursor c=db.rawQuery("SELECT i.currency,COALESCE(SUM(ii.profit_snapshot),0) FROM invoices i JOIN invoice_items ii ON ii.invoice_id=i.invoice_id WHERE i.invoice_type='SALE' AND i.created_at>=? AND i.created_at<? GROUP BY i.currency",new String[]{String.valueOf(from),String.valueOf(to)})){while(c.moveToNext())out.set(c.getString(0),c.getDouble(1));}}
 private static void readRepairProfit(SQLiteDatabase db,long from,long to,Money out){try(Cursor c=db.rawQuery("SELECT r.currency,COALESCE(SUM(r.total-COALESCE(pc.parts_cost,0)),0) FROM repairs r LEFT JOIN (SELECT repair_id,SUM(quantity*unit_cost) parts_cost FROM repair_parts GROUP BY repair_id) pc ON pc.repair_id=r.repair_id WHERE r.deleted_at IS NULL AND r.received_at>=? AND r.received_at<? GROUP BY r.currency",new String[]{String.valueOf(from),String.valueOf(to)})){while(c.moveToNext())out.set(c.getString(0),c.getDouble(1));}}
 private static void readCarry(SQLiteDatabase db,String day,Money out){try(Cursor c=db.rawQuery("SELECT opening_usd,opening_try,opening_syp FROM work_days WHERE business_day=? LIMIT 1",new String[]{day})){if(c.moveToFirst()){out.usd=c.getDouble(0);out.tryAmount=c.getDouble(1);out.syp=c.getDouble(2);}}}
 private static long atStart(String day){try{return new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).parse(day).getTime();}catch(Exception e){throw new IllegalArgumentException("تاريخ اليوم غير صالح");}}
 public static final class Summary{public final Money carry=new Money(),sales=new Money(),repairs=new Money(),saleProfit=new Money(),repairProfit=new Money();}
 public static final class Money{public double usd,tryAmount,syp;void set(String c,double v){if("TRY".equals(c))tryAmount=v;else if("SYP".equals(c))syp=v;else usd=v;}}
}
