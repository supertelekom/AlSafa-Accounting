package com.alsafa.native4.core;

import android.app.Activity;
import com.google.firebase.auth.FirebaseAuth;
import java.util.concurrent.atomic.AtomicBoolean;

/** Lightweight lifecycle sync. Never blocks UI and never overlaps another automatic cycle. */
public final class Native4AutoSync {
 private static final long MIN_INTERVAL_MS=30_000L;
 private static final AtomicBoolean RUNNING=new AtomicBoolean(false);
 private static volatile long lastStartedAt=0L;
 private Native4AutoSync(){}
 public static void request(Activity activity,CoreDb db,Runnable refresh){
  if(activity==null||db==null||FirebaseAuth.getInstance().getCurrentUser()==null)return;
  long now=System.currentTimeMillis();if(now-lastStartedAt<MIN_INTERVAL_MS||!RUNNING.compareAndSet(false,true))return;lastStartedAt=now;
  new Thread(()->{try{new Native4SyncCoordinator(db).syncOnce();}catch(Exception ignored){}finally{RUNNING.set(false);if(refresh!=null)activity.runOnUiThread(refresh);}},"native4-auto-sync").start();
 }
}
