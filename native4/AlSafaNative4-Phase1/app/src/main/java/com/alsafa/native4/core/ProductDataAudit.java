package com.alsafa.native4.core;
import android.database.Cursor;
import java.util.ArrayList;
import java.util.List;
/** Read-only catalog checks. Never merges, deletes, or changes stock. */
public final class ProductDataAudit {
 private ProductDataAudit(){}
 public static final class Finding {
  public final String category,detail;
  Finding(String category,String detail){this.category=category;this.detail=detail;}
 }
 public static List<Finding> inspect(CoreDb core){
  ArrayList<Finding> result=new ArrayList<>();
  addGroups(core,result,"باركود مكرر","SELECT TRIM(barcode),COUNT(*) FROM products WHERE deleted_at IS NULL AND barcode IS NOT NULL AND TRIM(barcode)<>'' GROUP BY TRIM(barcode) HAVING COUNT(*)>1");
  addGroups(core,result,"مرجع مكرر","SELECT TRIM(ref),COUNT(*) FROM products WHERE deleted_at IS NULL AND ref IS NOT NULL AND TRIM(ref)<>'' GROUP BY TRIM(ref) HAVING COUNT(*)>1");
  addGroups(core,result,"اسم مكرر للمراجعة","SELECT LOWER(TRIM(name)),COUNT(*) FROM products WHERE deleted_at IS NULL GROUP BY LOWER(TRIM(name)) HAVING COUNT(*)>1");
  try(Cursor c=core.db().rawQuery("SELECT product_id,name FROM products WHERE deleted_at IS NULL AND TRIM(name)=''",null)){
   while(c.moveToNext())result.add(new Finding("اسم فارغ",c.getString(1)+" — "+c.getString(0)));
  }
  try(Cursor c=core.db().rawQuery("SELECT p.product_id,p.name FROM products p LEFT JOIN products parent ON parent.product_id=p.parent_product_id AND parent.deleted_at IS NULL WHERE p.deleted_at IS NULL AND p.parent_product_id IS NOT NULL AND TRIM(p.parent_product_id)<>'' AND parent.product_id IS NULL",null)){
   while(c.moveToNext())result.add(new Finding("نسخة إضافية دون منتج أساسي",c.getString(1)+" — "+c.getString(0)));
  }
  return result;
 }
 private static void addGroups(CoreDb core,List<Finding> out,String category,String sql){
  try(Cursor c=core.db().rawQuery(sql,null)){while(c.moveToNext())out.add(new Finding(category,c.getString(0)+" ("+c.getInt(1)+" منتجات)"));}
 }
}
