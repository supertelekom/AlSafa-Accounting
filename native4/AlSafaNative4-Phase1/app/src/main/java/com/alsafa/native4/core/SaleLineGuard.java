package com.alsafa.native4.core;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
/** Pure validation for a sale before any database write. */
public final class SaleLineGuard {
 private SaleLineGuard(){}
 public static void requireUniqueProducts(List<String> productIds){
  if(productIds==null||productIds.isEmpty())throw new IllegalArgumentException("Sale requires items");
  Set<String> seen=new HashSet<>();
  for(String id:productIds){
   if(id==null)throw new IllegalArgumentException("Null product identifier");
   String key=id.trim();
   if(!key.isEmpty()&&!seen.add(key))throw new IllegalArgumentException("Duplicate product in sale");
  }
 }
 public static int remaining(int before,int sold){
  if(before<0||sold<=0||before<sold)throw new IllegalArgumentException("Insufficient stock");
  return before-sold;
 }
}
