package com.alsafa.native4.core;

import android.app.Activity;
import android.database.Cursor;
import org.json.JSONObject;
import com.google.firebase.auth.FirebaseAuth;
import java.util.concurrent.atomic.AtomicBoolean;

/** Lightweight lifecycle sync. Never blocks UI and never overlaps another automatic cycle. */
public final class Native4AutoSync {
 private static final long MIN_INTERVAL_MS=30_000L;
 private static final AtomicBoolean RUNNING=new AtomicBoolean(false);
 private static final AtomicBoolean IMMEDIATE_PENDING=new AtomicBoolean(false);
 private static volatile long lastStartedAt=0L;
 private static volatile String lastStatus="لم تبدأ المزامنة بعد";
 public static String status(){return lastStatus;}

 private Native4AutoSync(){}
 public static void request(Activity activity,CoreDb db,Runnable refresh){
  if(activity==null||db==null||FirebaseAuth.getInstance().getCurrentUser()==null)return;
  Native4BackgroundSyncWorker.cancelLegacyPeriodic(activity.getApplicationContext());
  Native4BackgroundSyncWorker.enqueue(activity.getApplicationContext());
  Native4ActiveSyncService.start(activity.getApplicationContext());
  long now=System.currentTimeMillis();if(now-lastStartedAt<MIN_INTERVAL_MS||!RUNNING.compareAndSet(false,true))return;lastStartedAt=now;
  lastStatus="المزامنة قيد التنفيذ";
  start(activity,db,refresh);
 }
 /** For a newly committed local operation. Unlike lifecycle refresh, this is not throttled. */
 public static void requestImmediate(Activity activity,CoreDb db,Runnable refresh){
  if(activity==null||db==null||FirebaseAuth.getInstance().getCurrentUser()==null)return;
  Native4BackgroundSyncWorker.enqueue(activity.getApplicationContext());
  Native4ActiveSyncService.start(activity.getApplicationContext());
  IMMEDIATE_PENDING.set(true);
  if(RUNNING.compareAndSet(false,true)){lastStatus="المزامنة قيد التنفيذ";start(activity,db,refresh);}
 }
 private static int pendingCount(CoreDb db){try(Cursor cursor=db.db().rawQuery("SELECT COUNT(*) FROM outbox WHERE state IN ('PENDING','RETRY','SENDING')",null)){return cursor.moveToFirst()?cursor.getInt(0):0;}}
 private static int failedCount(CoreDb db){try(Cursor cursor=db.db().rawQuery("SELECT COUNT(*) FROM outbox WHERE state='FAILED'",null)){return cursor.moveToFirst()?cursor.getInt(0):0;}}
 private static String operationLabel(String kind,String recordId,String payload){
  String label=operationName(kind);
  try{JSONObject j=new JSONObject(payload);String name="";if(kind!=null&&kind.startsWith("PRODUCT"))name=j.optString("name",j.optString("productName",""));else if(kind!=null&&(kind.startsWith("SALE")||kind.startsWith("REPAIR")))name=j.optString("invoiceNo",j.optString("invoiceNumber",""));else if("PURCHASE".equals(kind))name=j.optString("invoiceRef","");else if("INVENTORY_ADJUSTMENT".equals(kind))name=j.optString("productName","");if(!name.trim().isEmpty())return label+" — "+name.trim();}catch(Exception ignored){}
  return operationLabel(kind,recordId);
 }
 private static String operationLabel(String kind,String recordId){String label=operationName(kind);if(recordId==null||recordId.trim().isEmpty())return label;return label+" — "+recordId.trim();}
 private static String operationName(String kind){if(kind==null)return "عملية";switch(kind){case "SALE":case "SALE_CREATE":case "SALE_UPDATE":return "فاتورة مبيع";case "REPAIR":case "REPAIR_UPDATE":return "صيانة";case "PRODUCT":case "PRODUCT_CREATE":case "PRODUCT_UPDATE":case "PRODUCT_DELETE":return "منتج";case "PURCHASE":return "مشتريات";case "DAILY_CLOSING":case "CLOSING":return "إقفال يومي";case "CASH_MOVEMENT":case "CASH_MOVE":return "حركة صندوق";case "INVENTORY_ADJUSTMENT":return "تعديل مخزون";case "SETTING":return "إعدادات";default:return kind;}}
 private static void start(Activity activity,CoreDb db,Runnable refresh){
  new Thread(()->{
   try{
    do {
     IMMEDIATE_PENDING.set(false);
     try{
      Native4SyncCoordinator.Result result=new Native4SyncCoordinator(db,activity.getApplicationContext()).syncOnce((kind,id,payload,index,total)->{lastStatus="جاري رفع "+operationLabel(kind,id,payload)+" ("+index+" من "+total+")";if(refresh!=null)activity.runOnUiThread(refresh);},()->{lastStatus="جاري استقبال العمليات من Firebase";if(refresh!=null)activity.runOnUiThread(refresh);},(kind,id,payload,index,total)->{lastStatus="جاري استقبال "+operationLabel(kind,id,payload)+" ("+index+" من "+total+")";if(refresh!=null)activity.runOnUiThread(refresh);});
      int pending=pendingCount(db),failed=failedCount(db);
      lastStatus=(failed>0?"توجد عمليات تحتاج مراجعة":pending==0?"تمت مزامنة هذا الجهاز مع Firebase":"هناك عمليات تنتظر المزامنة")+" — رفع "+result.pushed+"، استقبال "+result.pulled+"، متبقي "+pending+"، إعادة محاولة "+result.retries+"، للمراجعة "+failed;
     }catch(Exception error){
      lastStatus="فشل المزامنة: "+error.getClass().getSimpleName();
     }
    }while(IMMEDIATE_PENDING.get());
   }finally{
    RUNNING.set(false);
    if(IMMEDIATE_PENDING.get()&&RUNNING.compareAndSet(false,true)){start(activity,db,refresh);return;}
    if(refresh!=null)activity.runOnUiThread(refresh);
   }
  },"native4-auto-sync").start();
 }

}
