package com.alsafa.native4.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class MoneyPrecisionTest {
 @Test public void exactDecimalConversion(){assertEquals(1055L,MoneyPrecision.minorUnits("10.55"));assertEquals("10.55",MoneyPrecision.amount(1055L));}
 @Test(expected=ArithmeticException.class) public void rejectsExcessFraction(){MoneyPrecision.minorUnits("10.555");}
 @Test public void balancedEntriesPass(){MoneyPrecision.requireBalanced(100,100);}
 @Test(expected=IllegalStateException.class) public void unbalancedEntriesFail(){MoneyPrecision.requireBalanced(100,99);}
}
