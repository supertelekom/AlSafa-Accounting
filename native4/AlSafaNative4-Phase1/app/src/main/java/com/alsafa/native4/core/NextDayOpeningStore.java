package com.alsafa.native4.core;

/** Opens the next work day from the latest immutable closing carry only. */
public final class NextDayOpeningStore {
 private final DailyClosingStore closingStore;
 private final ClosingCarryStore carryStore;
 public NextDayOpeningStore(CoreStore.DbProvider provider,String deviceId){
  this.closingStore=new DailyClosingStore(provider,deviceId);
  this.carryStore=new ClosingCarryStore(provider);
 }
 public ClosingCarryStore.Carry preview(){return carryStore.latest();}
 public ClosingCarryStore.Carry open(String businessDay)throws Exception{
  ClosingCarryStore.Carry c=carryStore.latest();
  if(c==null)throw new IllegalStateException("لا يوجد إقفال سابق يحتوي مدوّراً");
  closingStore.open(businessDay,c.usd,c.tryAmount,c.syp,c.closingId);
  return c;
 }
}
