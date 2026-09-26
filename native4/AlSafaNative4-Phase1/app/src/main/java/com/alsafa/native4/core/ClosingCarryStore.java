package com.alsafa.native4.core;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Read-only bridge from the latest immutable closing to the next work-day opening. */
public final class ClosingCarryStore {
 private final CoreStore.DbProvider provider;
 public ClosingCarryStore(CoreStore.DbProvider provider){this.provider=provider;}

 public Carry latest(){
  SQLiteDatabase db=provider.db();
  try(Cursor c=db.rawQuery("SELECT closing_id,business_day,carry_usd,carry_try,carry_syp FROM daily_closings ORDER BY closed_at DESC LIMIT 1",null)){
   if(!c.moveToFirst())return null;
   return new Carry(c.getString(0),c.getString(1),c.getDouble(2),c.getDouble(3),c.getDouble(4));
  }
 }

 public static final class Carry {
  public final String closingId,businessDay;
  public final double usd,tryAmount,syp;
  Carry(String closingId,String businessDay,double usd,double tryAmount,double syp){
   this.closingId=closingId;this.businessDay=businessDay;this.usd=usd;this.tryAmount=tryAmount;this.syp=syp;
  }
 }
}
