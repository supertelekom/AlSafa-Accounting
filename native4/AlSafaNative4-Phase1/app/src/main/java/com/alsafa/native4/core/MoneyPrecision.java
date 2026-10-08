package com.alsafa.native4.core;
import java.math.BigDecimal;
import java.math.RoundingMode;
public final class MoneyPrecision {
 private MoneyPrecision(){}
 public static long minorUnits(String amount){return new BigDecimal(amount).setScale(2,RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();}
 public static String amount(long units){return BigDecimal.valueOf(units,2).toPlainString();}
 public static void requireBalanced(long debit,long credit){if(debit!=credit)throw new IllegalStateException("Unbalanced journal entry");}
}
