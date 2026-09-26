package com.alsafa.native4.core;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class DailyClosingMathTest {
 @Test public void expectedIsOpeningPlusTodayMovementsOnly(){
  assertEquals(115.0,DailyClosingStore.calculateExpected(110.0,20.0,15.0),0.000001);
 }
 @Test public void carriedTryAndSypRemainWhenDayHasNoMovements(){
  assertEquals(110.0,DailyClosingStore.calculateExpected(110.0,0.0,0.0),0.000001);
  assertEquals(100.0,DailyClosingStore.calculateExpected(100.0,0.0,0.0),0.000001);
 }
 @Test public void transfersOutAfterOpeningReduceOnlyCurrentDayExpected(){
  assertEquals(30.0,DailyClosingStore.calculateExpected(50.0,0.0,20.0),0.000001);
 }
}
