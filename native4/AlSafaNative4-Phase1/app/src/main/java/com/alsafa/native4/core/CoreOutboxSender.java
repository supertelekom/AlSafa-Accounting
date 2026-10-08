package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.alsafa.native4.Native4Safety;
import com.alsafa.native4.sync.OutboxSender;
import java.util.ArrayList;
import java.util.List;

/** Drains the production-oriented CoreDb outbox through the same idempotent Firestore transport. */
public final class CoreOutboxSender {
 private final CoreDb coreDb; private final OutboxSender.Transport transport; private final String storeId;
 public CoreOutboxSender(CoreDb db,OutboxSender.Transport transport,String storeId){if(db==null||transport==null)throw new IllegalArgumentException("Sync dependencies مطلوبة");if(!Native4Safety.isIsolatedStore(storeId))throw new IllegalArgumentException("المزامنة الحالية محصورة بمتجر Native4 المعزول");this.coreDb=db;this.transport=transport;this.storeId=storeId;}
 public synchronized int drainOnce(){recoverStaleSending();int confirmed=0;for(Item item:pending()){if(!markSending(item.id))continue;OutboxSender.Command cmd=new OutboxSender.Command(item.id,storeId,item.deviceId,item.kind,item.recordId,item.payload);try{OutboxSender.Result r=transport.submit(cmd);confirm(item.id,r.revision);confirmed++;}catch(OutboxSender.RetryableException e){retry(item.id,e.getMessage());}catch(OutboxSender.PermanentException e){fail(item.id,e.getMessage());}catch(RuntimeException e){retry(item.id,"تعذر الإرسال: "+e.getClass().getSimpleName());}}return confirmed;}
 private List<Item> pending(){ArrayList<Item> out=new ArrayList<>();SQLiteDatabase db=coreDb.db();try(Cursor c=db.rawQuery("SELECT operation_id,operation_kind,record_id,payload,source_device_id FROM outbox WHERE state IN ('PENDING','RETRY') ORDER BY created_at LIMIT 100",null)){while(c.moveToNext())out.add(new Item(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4)));}return out;}
 private boolean markSending(String id){ContentValues v=new ContentValues();v.put("state","SENDING");v.put("last_attempt_at",System.currentTimeMillis());return coreDb.db().update("outbox",v,"operation_id=? AND state IN ('PENDING','RETRY')",new String[]{id})==1;}
 /** A process may die after claiming an operation. Restore only expired claims; transport must remain idempotent by operation_id. */
 private void recoverStaleSending(){long cutoff=System.currentTimeMillis()-120000L;ContentValues v=new ContentValues();v.put("state","RETRY");coreDb.db().update("outbox",v,"state='SENDING' AND (last_attempt_at IS NULL OR last_attempt_at<?)",new String[]{String.valueOf(cutoff)});}
 private void confirm(String id,long revision){SQLiteDatabase db=coreDb.db();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("state","CONFIRMED");v.put("confirmed_revision",revision);v.putNull("last_error");db.update("outbox",v,"operation_id=?",new String[]{id});ContentValues a=new ContentValues();a.put("operation_id",id);a.put("applied_at",System.currentTimeMillis());db.insertWithOnConflict("applied",null,a,SQLiteDatabase.CONFLICT_IGNORE);db.setTransactionSuccessful();}finally{db.endTransaction();}}
 private void retry(String id,String error){ContentValues v=new ContentValues();v.put("state","RETRY");v.put("last_error",error);coreDb.db().execSQL("UPDATE outbox SET attempts=attempts+1 WHERE operation_id=?",new Object[]{id});coreDb.db().update("outbox",v,"operation_id=?",new String[]{id});}
 private void fail(String id,String error){ContentValues v=new ContentValues();v.put("state","FAILED");v.put("last_error",error);coreDb.db().update("outbox",v,"operation_id=?",new String[]{id});}
 private static final class Item{final String id,kind,recordId,payload,deviceId;Item(String i,String k,String r,String p,String d){id=i;kind=k;recordId=r;payload=p;deviceId=d;}}
}
