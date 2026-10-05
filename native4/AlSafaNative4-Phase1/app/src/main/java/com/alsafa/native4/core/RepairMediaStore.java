package com.alsafa.native4.core;

import android.content.ContentValues;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import java.util.ArrayList;import java.util.List;import java.util.UUID;
/** Local metadata for repair intake photos. File/URI capture stays in the Android UI; accounting is unaffected. */
public final class RepairMediaStore {
 private final CoreStore.DbProvider provider;public RepairMediaStore(CoreStore.DbProvider provider){this.provider=provider;}
 public static final class Item{public final String mediaId,repairId,uri,source;public final long createdAt;Item(String m,String r,String u,String s,long t){mediaId=m;repairId=r;uri=u;source=s;createdAt=t;}}
 public String add(String repairId,String uri,String source){String r=req(repairId,"معرف الصيانة"),u=req(uri,"الصورة"),s=req(source,"مصدر الصورة").toUpperCase();if(!"CAMERA".equals(s)&&!"GALLERY".equals(s))throw new IllegalArgumentException("مصدر الصورة غير صالح");SQLiteDatabase db=provider.db();String id="repairmedia-"+UUID.randomUUID();ContentValues v=new ContentValues();v.put("media_id",id);v.put("repair_id",r);v.put("uri",u);v.put("source",s);v.put("created_at",System.currentTimeMillis());db.insertOrThrow("repair_media",null,v);return id;}
 public List<Item> list(String repairId){ArrayList<Item> out=new ArrayList<>();try(Cursor c=provider.db().rawQuery("SELECT media_id,repair_id,uri,source,created_at FROM repair_media WHERE repair_id=? ORDER BY created_at",new String[]{req(repairId,"معرف الصيانة")})){while(c.moveToNext())out.add(new Item(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4)));}return out;}
 public void removeForRepair(String repairId){provider.db().delete("repair_media","repair_id=?",new String[]{req(repairId,"معرف الصيانة")});}
 private static String req(String v,String label){String s=v==null?"":v.trim();if(s.isEmpty())throw new IllegalArgumentException(label+" مطلوب");return s;}
}
