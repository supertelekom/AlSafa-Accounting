package com.alsafa.native4.core;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/** Read-only product lookup shared by Core sales and purchases. No cloud reads and no destructive reconciliation. */
public final class CoreProductCatalog {
 private final CoreStore.DbProvider provider;
 public CoreProductCatalog(CoreStore.DbProvider provider){this.provider=provider;}

 public List<ProductHit> search(String query,int limit){
  String q=query==null?"":query.trim();int safeLimit=Math.max(1,Math.min(limit,50));SQLiteDatabase db=provider.db();List<ProductHit> out=new ArrayList<>();
  String sql="SELECT product_id,name,ref,barcode,brand,quantity,sale_price,cost_price,currency,gender,display_type,dial_color,strap_type,case_color FROM products WHERE deleted_at IS NULL";
  String[] args;
  if(q.isEmpty()){sql+=" ORDER BY updated_at DESC LIMIT ?";args=new String[]{String.valueOf(safeLimit)};}
  else{sql+=" AND (name LIKE ? OR ref LIKE ? OR barcode LIKE ? OR brand LIKE ?) ORDER BY CASE WHEN barcode=? THEN 0 WHEN ref=? THEN 1 ELSE 2 END,name COLLATE NOCASE LIMIT ?";String like="%"+escapeLike(q)+"%";args=new String[]{like,like,like,like,q,q,String.valueOf(safeLimit)};}
  try(Cursor c=db.rawQuery(sql,args)){while(c.moveToNext())out.add(new ProductHit(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getInt(5),c.getDouble(6),c.getDouble(7),c.getString(8),c.getString(9),c.getString(10),c.getString(11),c.getString(12),c.getString(13)));}
  return out;
 }

 public ProductHit get(String productId){String id=productId==null?"":productId.trim();if(id.isEmpty())return null;try(Cursor c=provider.db().rawQuery("SELECT product_id,name,ref,barcode,brand,quantity,sale_price,cost_price,currency FROM products WHERE product_id=? AND deleted_at IS NULL LIMIT 1",new String[]{id})){return c.moveToFirst()?new ProductHit(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getInt(5),c.getDouble(6),c.getDouble(7),c.getString(8)):null;}}
 private static String escapeLike(String s){return s.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");}
 public static final class ProductHit{
  public final String id,name,ref,barcode,brand,currency,gender,displayType,dialColor,strapType,caseColor;public final int quantity;public final double salePrice,costPrice;
  ProductHit(String id,String name,String ref,String barcode,String brand,int quantity,double salePrice,double costPrice,String currency,String gender,String displayType,String dialColor,String strapType,String caseColor){this.id=id;this.name=name;this.ref=ref;this.barcode=barcode;this.brand=brand;this.quantity=quantity;this.salePrice=salePrice;this.costPrice=costPrice;this.currency=currency;this.gender=clean(gender);this.displayType=clean(displayType);this.dialColor=clean(dialColor);this.strapType=clean(strapType);this.caseColor=clean(caseColor);}\n  private static String clean(String s){return s==null?"":s;}
  public String label(){String code=!ref.isEmpty()?ref:barcode;return name+(code.isEmpty()?"":" — "+code)+" — المخزون: "+quantity;}
 }
}
