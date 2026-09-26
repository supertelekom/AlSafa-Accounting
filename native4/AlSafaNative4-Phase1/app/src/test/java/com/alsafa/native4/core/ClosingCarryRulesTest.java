package com.alsafa.native4.core;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class ClosingCarryRulesTest {
 @Test public void onlyExplicitCarryMovesToNextDay(){
  double counted=500.0,carry=100.0;
  assertEquals(400.0,DailyClosingStore.drawerOut(counted,carry),0.000001);
  assertEquals(100.0,DailyClosingStore.calculateExpected(carry,0.0,0.0),0.000001);
 }
 @Test public void currenciesRemainIndependent(){
  assertEquals(25.0,DailyClosingStore.calculateExpected(20.0,5.0,0.0),0.000001);
  assertEquals(110.0,DailyClosingStore.calculateExpected(110.0,0.0,0.0),0.000001);
  assertEquals(100.0,DailyClosingStore.calculateExpected(100.0,0.0,0.0),0.000001);
 }
}
