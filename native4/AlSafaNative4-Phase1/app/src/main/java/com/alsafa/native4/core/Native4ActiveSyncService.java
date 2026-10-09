package com.alsafa.native4.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import com.google.firebase.auth.FirebaseAuth;
import java.util.concurrent.atomic.AtomicBoolean;

/** Foreground sync while the operator moves between the shop app and WhatsApp. */
public final class Native4ActiveSyncService extends Service {
 private static final String CHANNEL="native4_active_sync";
 private static final int NOTIFICATION_ID=4104;
 private final AtomicBoolean running=new AtomicBoolean(false);
 public static void start(Context context){
  Intent intent=new Intent(context,Native4ActiveSyncService.class);
  try{androidx.core.content.ContextCompat.startForegroundService(context,intent);}
  catch(RuntimeException e){android.util.Log.w("Native4Sync","Foreground start deferred to WorkManager",e);Native4BackgroundSyncWorker.enqueue(context);}
 }
 @Override public void onCreate(){
  super.onCreate();
  NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"مزامنة الصفا",NotificationManager.IMPORTANCE_LOW));
  Notification notification=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_notify_sync)
   .setContentTitle("الصفا للساعات").setContentText("المزامنة تعمل في الخلفية").setOngoing(true).build();
  startForeground(NOTIFICATION_ID,notification);
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(running.compareAndSet(false,true)){
   new Thread(()->{
    try{
     while(!Thread.currentThread().isInterrupted()){
      if(FirebaseAuth.getInstance().getCurrentUser()!=null){
       try{new Native4SyncCoordinator(new CoreDb(getApplicationContext()),getApplicationContext()).syncOnce();}
       catch(Exception e){android.util.Log.w("Native4Sync","Foreground sync retry pending",e);}
      }
      SystemClock.sleep(30_000L);
     }
    }finally{running.set(false);}
   },"native4-active-sync").start();
  }
  return START_STICKY;
 }
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public void onDestroy(){Native4BackgroundSyncWorker.enqueue(getApplicationContext());super.onDestroy();}
}
