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
 private Native4AutoSync(){}
 public static void request(Activity activity,CoreDb db,Runnable refresh){
  if(activity==null||db==null||FirebaseAuth.getInstance().getCurrentUser()==null)return;
  long now=System.currentTimeMillis();if(now-lastStartedAt<MIN_INTERVAL_MS||!RUNNING.compareAndSet(false,true))return;lastStartedAt=now;
  start(activity,db,refresh);
 }
 /** For a newly committed local operation. Unlike lifecycle refresh, this is not throttled. */
 public static void requestImmediate(Activity activity,CoreDb db,Runnable refresh){
  if(activity==null||db==null||FirebaseAuth.getInstance().getCurrentUser()==null)return;
  IMMEDIATE_PENDING.set(true);
  if(RUNNING.compareAndSet(false,true))start(activity,db,refresh);
 }
 private static void start(Activity activity,CoreDb db,Runnable refresh){
  new Thread(()->{
   try{
    do {
     IMMEDIATE_PENDING.set(false);
     try{new Native4SyncCoordinator(db).syncOnce();}catch(Exception ignored){}
    }while(IMMEDIATE_PENDING.get());
   }finally{
    RUNNING.set(false);
    if(IMMEDIATE_PENDING.get()&&RUNNING.compareAndSet(false,true)){start(activity,db,refresh);return;}
    if(refresh!=null)activity.runOnUiThread(refresh);
   }
  },"native4-auto-sync").start();
 }

}
