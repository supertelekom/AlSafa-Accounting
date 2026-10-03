package com.alsafa.native4.core;

/** Central money-account rules shared by sales, repairs, transfers and settlements. */
public final class MoneyRules {
 private MoneyRules(){}
 public static String currency(String value){String c=value==null?"":value.trim().toUpperCase();if(!c.equals("USD")&&!c.equals("TRY")&&!c.equals("SYP"))throw new IllegalArgumentException("العملة غير مدعومة");return c;}
 public static String account(String value){String a=value==null?"":value.trim().toUpperCase();if(a.isEmpty())throw new IllegalArgumentException("الحساب/الخزنة مطلوب");return a;}
 public static void requireSupported(String account,String currency){String a=account(account),c=currency(currency);if("SHAM_CASH".equals(a)&&"TRY".equals(c))throw new IllegalArgumentException("شام كاش يدعم الدولار والليرة السورية فقط؛ الليرة التركية غير مدعومة");}
 public static void requirePaymentSupported(String paymentMethod,String currency){String p=paymentMethod==null?"":paymentMethod.trim().toUpperCase();if("SHAM_CASH".equals(p))requireSupported("SHAM_CASH",currency);}
}
