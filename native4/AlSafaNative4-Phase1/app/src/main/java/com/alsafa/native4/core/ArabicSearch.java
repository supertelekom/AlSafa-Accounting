package com.alsafa.native4.core;
import java.util.Locale;
/** Search-only Arabic normalization. Never rewrites persisted names or identifiers. */
public final class ArabicSearch {
 private ArabicSearch(){}
 public static boolean hasArabic(String text){return text!=null&&text.matches(".*[\\u0600-\\u06FF].*");}
 public static String normalize(String text){
  if(text==null)return "";
  return text.toLowerCase(Locale.ROOT).replaceAll("[\\u064B-\\u065F\\u0670\\u0640]","").replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ٱ','ا').replace('ى','ي').replace('ؤ','و').replace('ئ','ي').replace('ة','ه').trim().replaceAll("\\s+"," ");
 }
 public static boolean matches(String normalizedQuery,String... values){
  if(normalizedQuery==null||normalizedQuery.isEmpty())return false;
  String[] terms=normalizedQuery.split(" ");for(String value:values){String normalized=normalize(value);boolean all=true;for(String term:terms)if(!normalized.contains(term)){all=false;break;}if(all)return true;}
  return false;
 }
}
