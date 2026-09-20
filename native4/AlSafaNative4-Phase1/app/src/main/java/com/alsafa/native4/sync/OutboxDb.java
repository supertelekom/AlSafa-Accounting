package com.alsafa.native4.sync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** Durable queue. A command is retained until the server acknowledges its exact operationId. */
public final class OutboxDb extends SQLiteOpenHelper {
 public static final class Item {
  public final String id, kind, recordId, payload; public final int attempts;
  Item(String id,String kind,String recordId,String payload,int attempts){this.id=id;this.kind=kind;this.recordId=recordId;this.payload=payload;this.attempts=attempts;}
 }
 public OutboxDb(Context context){super(context,"alsafa_native4.db",null,3);}
 @Override public void onCreate(SQLiteDatabase db){
  db.execSQL("CREATE TABLE outbox(operation_id TEXT PRIMARY KEY, operation_kind TEXT NOT NULL, record_id TEXT NOT NULL, payload TEXT NOT NULL, created_at INTEGER NOT NULL, state TEXT NOT NULL DEFAULT 'PENDING', attempts INTEGER NOT NULL DEFAULT 0, last_attempt_at INTEGER, last_error TEXT)");
  db.execSQL("CREATE TABLE applied(operation_id TEXT PRIMARY KEY, applied_at INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE sync_checkpoint(checkpoint_key TEXT PRIMARY KEY, checkpoint_value TEXT NOT NULL, updated_at INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE audit_log(id INTEGER PRIMARY KEY AUTOINCREMENT, happened_at INTEGER NOT NULL, direction TEXT NOT NULL, operation_id TEXT NOT NULL, operation_kind TEXT NOT NULL, record_id TEXT NOT NULL, detail TEXT NOT NULL)");
 }
 @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
  if(oldVersion<2){
   db.execSQL("ALTER TABLE outbox ADD COLUMN operation_kind TEXT NOT NULL DEFAULT 'LEGACY'");
   db.execSQL("ALTER TABLE outbox ADD COLUMN state TEXT NOT NULL DEFAULT 'PENDING'");
   db.execSQL("ALTER TABLE outbox ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0");
   db.execSQL("ALTER TABLE outbox ADD COLUMN last_attempt_at INTEGER");
   db.execSQL("ALTER TABLE outbox ADD COLUMN last_error TEXT");
   db.execSQL("UPDATE outbox SET operation_kind=collection_name WHERE operation_kind='LEGACY'");
   db.execSQL("UPDATE outbox SET state=CASE WHEN acked=1 THEN 'CONFIRMED' ELSE 'PENDING' END");
   db.execSQL("CREATE TABLE IF NOT EXISTS sync_checkpoint(checkpoint_key TEXT PRIMARY KEY, checkpoint_value TEXT NOT NULL, updated_at INTEGER NOT NULL)");
  }
  if(oldVersion<3) db.execSQL("CREATE TABLE IF NOT EXISTS audit_log(id INTEGER PRIMARY KEY AUTOINCREMENT, happened_at INTEGER NOT NULL, direction TEXT NOT NULL, operation_id TEXT NOT NULL, operation_kind TEXT NOT NULL, record_id TEXT NOT NULL, detail TEXT NOT NULL)");
 }
 public synchronized void enqueue(SyncOperation operation){
  operation.validate(); ContentValues values=new ContentValues();
  values.put("operation_id",operation.id); values.put("operation_kind",operation.kind.name()); values.put("record_id",operation.recordId);
  values.put("payload",operation.payload); values.put("created_at",operation.createdAt); values.put("state","PENDING");
  if(writable().insertWithOnConflict("outbox",null,values,SQLiteDatabase.CONFLICT_IGNORE)!=-1) audit("OUTBOX",operation.id,operation.kind.name(),operation.recordId,"محفوظ محلياً وينتظر التأكيد");
 }
 public synchronized List<Item> pending(){
  ArrayList<Item> items=new ArrayList<>();
  try(Cursor c=writable().rawQuery("SELECT operation_id,operation_kind,record_id,payload,attempts FROM outbox WHERE state='PENDING' ORDER BY created_at",null)){
   while(c.moveToNext())items.add(new Item(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getInt(4)));
  }
  return items;
 }
 public synchronized void markAttempt(String operationId){writable().execSQL("UPDATE outbox SET attempts=attempts+1,last_attempt_at=? WHERE operation_id=?",new Object[]{System.currentTimeMillis(),operationId});}
 public synchronized void retryLater(String operationId,String error){ContentValues v=new ContentValues();v.put("state","PENDING");v.put("last_error",safeError(error));writable().update("outbox",v,"operation_id=?",new String[]{operationId});}
 public synchronized void confirm(String operationId){ContentValues v=new ContentValues();v.put("state","CONFIRMED");v.putNull("last_error");writable().update("outbox",v,"operation_id=?",new String[]{operationId}); try(Cursor c=writable().rawQuery("SELECT operation_kind,record_id FROM outbox WHERE operation_id=?",new String[]{operationId})){if(c.moveToFirst())audit("CONFIRMED",operationId,c.getString(0),c.getString(1),"تم تأكيد الإرسال");}}
 public synchronized boolean wasApplied(String operationId){try(Cursor c=writable().rawQuery("SELECT 1 FROM applied WHERE operation_id=? LIMIT 1",new String[]{operationId})){return c.moveToFirst();}}
 public synchronized void markApplied(String operationId){ContentValues v=new ContentValues();v.put("operation_id",operationId);v.put("applied_at",System.currentTimeMillis());writable().insertWithOnConflict("applied",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
 public synchronized void markReceived(String operationId,String kind,String recordId){if(wasApplied(operationId))return;markApplied(operationId);audit("RECEIVED",operationId,kind,recordId,"تم الاستلام من الجهاز الآخر");}
 public synchronized void saveCheckpoint(String key,String value){ContentValues v=new ContentValues();v.put("checkpoint_key",key);v.put("checkpoint_value",value);v.put("updated_at",System.currentTimeMillis());writable().insertWithOnConflict("sync_checkpoint",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 public synchronized String checkpoint(String key){try(Cursor c=writable().rawQuery("SELECT checkpoint_value FROM sync_checkpoint WHERE checkpoint_key=?",new String[]{key})){return c.moveToFirst()?c.getString(0):"0";}}
 public synchronized int pendingCount(){try(Cursor c=writable().rawQuery("SELECT COUNT(*) FROM outbox WHERE state='PENDING'",null)){return c.moveToFirst()?c.getInt(0):0;}}
 public synchronized int confirmedCount(){try(Cursor c=writable().rawQuery("SELECT COUNT(*) FROM outbox WHERE state='CONFIRMED'",null)){return c.moveToFirst()?c.getInt(0):0;}}
 public synchronized String recentAudit(){StringBuilder out=new StringBuilder();try(Cursor c=writable().rawQuery("SELECT direction,operation_kind,record_id,detail,happened_at FROM audit_log ORDER BY id DESC LIMIT 8",null)){while(c.moveToNext()){out.append(c.getString(0)).append(" · ").append(c.getString(1)).append(" / ").append(c.getString(2)).append("\n").append(c.getString(3)).append("\n\n");}}return out.length()==0?"لا يوجد سجل اختبار بعد":out.toString();}
 private void audit(String direction,String operationId,String kind,String recordId,String detail){ContentValues v=new ContentValues();v.put("happened_at",System.currentTimeMillis());v.put("direction",direction);v.put("operation_id",operationId);v.put("operation_kind",kind);v.put("record_id",recordId);v.put("detail",detail);writable().insert("audit_log",null,v);}
 private SQLiteDatabase writable(){return getWritableDatabase();}
 private static String safeError(String error){String value=String.valueOf(error);return value.substring(0,Math.min(500,value.length()));}
}
