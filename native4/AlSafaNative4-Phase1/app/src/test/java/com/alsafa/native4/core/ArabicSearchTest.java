package com.alsafa.native4.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class ArabicSearchTest {
 @Test public void ignoresHamzaAndDiacritics(){
  assertTrue(ArabicSearch.matches(ArabicSearch.normalize("كاسيو اماراتي"),"كاسيو إِمَارَاتِيّ"));
  assertTrue(ArabicSearch.matches(ArabicSearch.normalize("بطاريه"),"بطارية"));
 }
 @Test public void allowsReorderedWords(){
  assertTrue(ArabicSearch.matches(ArabicSearch.normalize("اماراتي كاسيو"),"كاسيو إماراتي رجالي"));
 }
 @Test public void keepsDistinctWordsDistinct(){
  assertFalse(ArabicSearch.matches(ArabicSearch.normalize("كاسيو سيتيزن"),"كاسيو إماراتي"));
 }
 @Test public void handlesEmptyAndNull(){
  assertFalse(ArabicSearch.matches(ArabicSearch.normalize(""),"كاسيو"));
  assertFalse(ArabicSearch.matches(ArabicSearch.normalize("كاسيو"),(String)null));
 }
}
