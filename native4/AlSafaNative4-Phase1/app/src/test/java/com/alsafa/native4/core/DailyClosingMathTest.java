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
 @Test public void drawerOutIsCountedMinusExplicitCarry(){
  assertEquals(70.0,DailyClosingStore.drawerOut(100.0,30.0),0.000001);
 }
 @Test(expected=IllegalArgumentException.class) public void carryCannotExceedCounted(){
  DailyClosingStore.drawerOut(20.0,20.01);
 }
 @Test(expected=IllegalArgumentException.class) public void carryCannotBeNegative(){
  DailyClosingStore.drawerOut(20.0,-1.0);
 }
}
