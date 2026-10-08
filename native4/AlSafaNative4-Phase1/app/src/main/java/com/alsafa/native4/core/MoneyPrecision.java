package com.alsafa.native4.core;
import java.math.BigDecimal;
import java.math.RoundingMode;
/** Exact financial amount conversion. Monetary policy is explicit per currency. */
public final class MoneyPrecision {
 private MoneyPrecision(){}
 public static int scale(String currency){
  String c=MoneyRules.currency(currency);
  return "SYP".equals(c)?0:2;
 }
 public static long minorUnits(String amount,String currency){
  if(amount==null||amount.trim().isEmpty())throw new IllegalArgumentException("Amount required");
  return new BigDecimal(amount.trim()).setScale(scale(currency),RoundingMode.UNNECESSARY).movePointRight(scale(currency)).longValueExact();
 }
 public static String amount(long units,String currency){return BigDecimal.valueOf(units,scale(currency)).toPlainString();}
 /** Legacy two-decimal overloads remain for callers already using them. */
 public static long minorUnits(String amount){return minorUnits(amount,"USD");}
 public static String amount(long units){return amount(units,"USD");}
 public static void requireBalanced(long debit,long credit){if(debit!=credit)throw new IllegalStateException("Unbalanced journal entry");}
}
