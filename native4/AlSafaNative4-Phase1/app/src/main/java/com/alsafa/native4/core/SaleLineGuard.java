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
 public static double balanceUsd(double invoiceUsd,double paidUsd){
  if(!Double.isFinite(invoiceUsd)||!Double.isFinite(paidUsd)||invoiceUsd<0||paidUsd<0)throw new IllegalArgumentException("Invalid settlement amounts");
  double due=invoiceUsd-paidUsd;
  if(!Double.isFinite(due)||due < -0.01)throw new IllegalArgumentException("Payment exceeds invoice");
  return Math.max(0,due);
 }
 public static double lineAmount(double unitPrice,int quantity){
  if(!Double.isFinite(unitPrice)||unitPrice<0||quantity<=0)throw new IllegalArgumentException("Invalid sale line");
  double amount=unitPrice*quantity;
  if(!Double.isFinite(amount))throw new IllegalArgumentException("Sale line overflow");
  return amount;
 }
 public static double addAmount(double first,double second){
  if(!Double.isFinite(first)||!Double.isFinite(second)||first<0||second<0)throw new IllegalArgumentException("Invalid sale sum");
  double total=first+second;
  if(!Double.isFinite(total))throw new IllegalArgumentException("Sale sum overflow");
  return total;
 }
 public static double payable(double subtotal,double discount){
  if(!Double.isFinite(subtotal)||subtotal<0||!Double.isFinite(discount)||discount<0||discount>subtotal)throw new IllegalArgumentException("Invalid sale amount or discount");
  double amount=subtotal-discount;
  if(!Double.isFinite(amount))throw new IllegalArgumentException("Invalid sale total");
  return amount;
 }
 public static int remaining(int before,int sold){
  if(before<0||sold<=0||before<sold)throw new IllegalArgumentException("Insufficient stock");
  return before-sold;
 }
}
