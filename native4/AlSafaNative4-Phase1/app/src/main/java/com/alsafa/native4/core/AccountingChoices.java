package com.alsafa.native4.core;

/** Single source of truth for user-facing fixed accounting choices and their stored codes. */
public final class AccountingChoices {
 private AccountingChoices(){}
 public static final String[] CURRENCY_LABELS={"دولار USD","ليرة تركية TRY","ليرة سورية SYP"};
 public static final String[] CURRENCY_CODES={"USD","TRY","SYP"};
 public static final String[] PURCHASE_SOURCE_LABELS={"الدرج النقدي","شام كاش","البنك","الصندوق الخارجي","آجل"};
 public static final String[] PURCHASE_SOURCE_CODES={"MAIN","SHAM_CASH","BANK","EXTERNAL","CREDIT"};
 public static final String[] PAYMENT_LABELS={"نقدي","شام كاش","آجل"};
 public static final String[] PAYMENT_CODES={"CASH","SHAM_CASH","CREDIT"};
 public static String codeAt(String[] codes,int position){if(position<0||position>=codes.length)throw new IllegalArgumentException("اختيار غير صالح");return codes[position];}
}
