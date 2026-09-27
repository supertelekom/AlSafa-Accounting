package com.alsafa.native4.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Test;

public class AccountingChoicesTest {
 @Test public void purchaseSourcesMapToStableStoredCodes(){
  assertEquals("MAIN",AccountingChoices.codeAt(AccountingChoices.PURCHASE_SOURCE_CODES,0));
  assertEquals("SHAM_CASH",AccountingChoices.codeAt(AccountingChoices.PURCHASE_SOURCE_CODES,1));
  assertEquals("BANK",AccountingChoices.codeAt(AccountingChoices.PURCHASE_SOURCE_CODES,2));
  assertEquals("EXTERNAL",AccountingChoices.codeAt(AccountingChoices.PURCHASE_SOURCE_CODES,3));
  assertEquals("CREDIT",AccountingChoices.codeAt(AccountingChoices.PURCHASE_SOURCE_CODES,4));
 }
 @Test public void currenciesMapToStableCodes(){
  assertEquals("USD",AccountingChoices.codeAt(AccountingChoices.CURRENCY_CODES,0));
  assertEquals("TRY",AccountingChoices.codeAt(AccountingChoices.CURRENCY_CODES,1));
  assertEquals("SYP",AccountingChoices.codeAt(AccountingChoices.CURRENCY_CODES,2));
 }
 @Test public void invalidPositionIsRejected(){
  try{AccountingChoices.codeAt(AccountingChoices.CURRENCY_CODES,-1);fail("expected rejection");}catch(IllegalArgumentException expected){}
  try{AccountingChoices.codeAt(AccountingChoices.CURRENCY_CODES,3);fail("expected rejection");}catch(IllegalArgumentException expected){}
 }
}
