package com.alsafa.native4.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class MoneyPrecisionTest {
 @Test public void exactDecimalConversion(){assertEquals(1055L,MoneyPrecision.minorUnits("10.55"));assertEquals("10.55",MoneyPrecision.amount(1055L));}
 @Test(expected=ArithmeticException.class) public void rejectsExcessFraction(){MoneyPrecision.minorUnits("10.555");}
 @Test public void syrianPoundUsesWholeUnits(){assertEquals(1500L,MoneyPrecision.minorUnits("1500","SYP"));assertEquals("1500",MoneyPrecision.amount(1500L,"SYP"));}
 @Test(expected=ArithmeticException.class) public void syrianPoundRejectsFractions(){MoneyPrecision.minorUnits("1500.50","SYP");}
 @Test public void liraAndDollarUseTwoDecimals(){assertEquals(1055L,MoneyPrecision.minorUnits("10.55","TRY"));assertEquals(1055L,MoneyPrecision.minorUnits("10.55","USD"));}
 @Test public void balancedEntriesPass(){MoneyPrecision.requireBalanced(100,100);}
 @Test(expected=IllegalStateException.class) public void unbalancedEntriesFail(){MoneyPrecision.requireBalanced(100,99);}
}
