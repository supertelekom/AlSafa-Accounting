package com.alsafa.native4.core;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.alsafa.native4.sync.SyncOperation;

/** Production core DB for Native4. Local data is authoritative; upgrades are additive. */
public final class CoreDb extends SQLiteOpenHelper implements CoreStore.DbProvider {
 public CoreDb(Context context){super(context,"alsafa_native4_core.db",null,9);}
 @Override public void onCreate(SQLiteDatabase db){createSyncTables(db);CoreSchemaV8.create(db);}
 @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){createSyncTables(db);CoreSchemaV8.create(db);}
 private static void createSyncTables(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS outbox(operation_id TEXT PRIMARY KEY,operation_kind TEXT NOT NULL,record_id TEXT NOT NULL,payload TEXT NOT NULL,source_device_id TEXT NOT NULL,created_at INTEGER NOT NULL,state TEXT NOT NULL DEFAULT 'PENDING',attempts INTEGER NOT NULL DEFAULT 0,last_attempt_at INTEGER,last_error TEXT,confirmed_revision INTEGER)");
  db.execSQL("CREATE TABLE IF NOT EXISTS applied(operation_id TEXT PRIMARY KEY,applied_at INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS sync_checkpoint(checkpoint_key TEXT PRIMARY KEY,checkpoint_value TEXT NOT NULL,updated_at INTEGER NOT NULL)");
 }
 @Override public SQLiteDatabase db(){return getWritableDatabase();}
 @Override public void enqueue(SQLiteDatabase db,SyncOperation op){op.validate();ContentValues v=new ContentValues();v.put("operation_id",op.id);v.put("operation_kind",op.kind.name());v.put("record_id",op.recordId);v.put("payload",op.payload);v.put("source_device_id",op.sourceDeviceId);v.put("created_at",op.createdAt);v.put("state","PENDING");if(db.insertWithOnConflict("outbox",null,v,SQLiteDatabase.CONFLICT_IGNORE)==-1)throw new IllegalStateException("تعذر حفظ أمر المزامنة");}
}
