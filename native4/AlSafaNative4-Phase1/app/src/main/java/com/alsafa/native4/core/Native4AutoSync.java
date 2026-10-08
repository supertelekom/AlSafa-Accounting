package com.alsafa.native4.core;

import android.app.Activity;
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
  long now=System.currentTimeMillis();if(now-lastStartedAt<MIN_INTERVAL_MS||!RUNNING.compareAndSet(false,true))return;lastStartedAt=now;
  lastStatus="المزامنة قيد التنفيذ";
  start(activity,db,refresh);
 }
 /** For a newly committed local operation. Unlike lifecycle refresh, this is not throttled. */
 public static void requestImmediate(Activity activity,CoreDb db,Runnable refresh){
  if(activity==null||db==null||FirebaseAuth.getInstance().getCurrentUser()==null)return;
  IMMEDIATE_PENDING.set(true);
  lastStatus="المزامنة مطلوبة";
  if(RUNNING.compareAndSet(false,true))start(activity,db,refresh);
 }
 private static String operationName(String kind){if(kind==null)return "عملية";switch(kind){case "SALE":return "فاتورة مبيع";case "REPAIR":return "صيانة";case "PRODUCT":return "منتج";case "PURCHASE":return "مشتريات";case "CLOSING":return "إقفال يومي";case "CASH_MOVE":return "حركة صندوق";default:return kind;}}
 private static void start(Activity activity,CoreDb db,Runnable refresh){
  new Thread(()->{
   try{
    do {
     IMMEDIATE_PENDING.set(false);
     try{
      Native4SyncCoordinator.Result result=new Native4SyncCoordinator(db).syncOnce((kind,id,index,total)->{lastStatus="جاري رفع "+operationName(kind)+" ("+index+" من "+total+")";if(refresh!=null)activity.runOnUiThread(refresh);});
      lastStatus=(result.retries==0&&result.failed==0?"انتهت المزامنة":"المزامنة غير مكتملة")+" — رفع "+result.pushed+"، استقبال "+result.pulled+"، إعادة محاولة "+result.retries+"، فشل "+result.failed;
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
