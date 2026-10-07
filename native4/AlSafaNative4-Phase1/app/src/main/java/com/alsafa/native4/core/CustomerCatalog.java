package com.alsafa.native4.core;
import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import java.util.*;
/** Read-only customer lookup for sale/repair UI. Never mutates customer or loyalty data. */
public final class CustomerCatalog{
 private final CoreStore.DbProvider provider;public CustomerCatalog(CoreStore.DbProvider p){provider=p;}
 public List<Hit> search(String query,int limit){String q=query==null?"":query.trim();ArrayList<Hit> out=new ArrayList<>();if(q.isEmpty())return out;String like="%"+q+"%";SQLiteDatabase db=provider.db();try(Cursor c=db.rawQuery("SELECT customer_id,name,COALESCE(phone,''),points FROM customers WHERE deleted_at IS NULL AND (name LIKE ? OR phone LIKE ?) ORDER BY name LIMIT ?",new String[]{like,like,String.valueOf(Math.max(1,limit))})){while(c.moveToNext())out.add(new Hit(c.getString(0),c.getString(1),c.getString(2),c.getDouble(3)));}return out;}
 public static final class Hit{public final String id,name,phone;public final double points;Hit(String i,String n,String p,double x){id=i;name=n;phone=p;points=x;}public String label(){return name+(phone.isEmpty()?"":" — "+phone)+" — النقاط: "+String.format(Locale.US,"%.0f",points);}}
}