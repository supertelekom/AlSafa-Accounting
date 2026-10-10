package com.alsafa.native4.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.os.IBinder;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.net.NetworkCapabilities;
import com.google.firebase.auth.FirebaseAuth;
import java.util.concurrent.atomic.AtomicBoolean;

/** Foreground sync while the operator moves between the shop app and WhatsApp. */
public final class Native4ActiveSyncService extends Service {
 private static final String CHANNEL="native4_active_sync";
 private static final int NOTIFICATION_ID=4104;
 private final AtomicBoolean running=new AtomicBoolean(false);
 private final Object wakeLock=new Object();
 private volatile boolean stopped=false;
 private volatile boolean syncRequested=true;
 private ConnectivityManager connectivity;
 private ConnectivityManager.NetworkCallback networkCallback;
 public static final String ACTION_SYNC_NOW="com.alsafa.native4.SYNC_NOW";
 public static void start(Context context){
  Intent intent=new Intent(context,Native4ActiveSyncService.class).setAction(ACTION_SYNC_NOW);
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
  connectivity=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
  networkCallback=new ConnectivityManager.NetworkCallback(){@Override public void onAvailable(Network network){synchronized(wakeLock){if(!stopped){syncRequested=true;wakeLock.notifyAll();}}}};
  if(connectivity!=null){try{connectivity.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),networkCallback);}catch(RuntimeException error){android.util.Log.w("Native4Sync","Network callback unavailable",error);}}
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  stopped=false;
  synchronized(wakeLock){syncRequested=true;wakeLock.notifyAll();}
  if(running.compareAndSet(false,true)){
   new Thread(()->{
    try{
     while(!stopped&&!Thread.currentThread().isInterrupted()){
      synchronized(wakeLock){while(!syncRequested&&!stopped){try{wakeLock.wait(60_000L);if(!stopped)syncRequested=true;}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return;}}syncRequested=false;}
      if(stopped)break;
      if(FirebaseAuth.getInstance().getCurrentUser()!=null){
       try(CoreDb db=new CoreDb(getApplicationContext())){
        Native4SyncCoordinator.Result result=new Native4SyncCoordinator(db,getApplicationContext()).syncOnce();
        boolean pending;
        try(Cursor cursor=db.db().rawQuery("SELECT 1 FROM outbox WHERE state IN ('PENDING','RETRY','SENDING') LIMIT 1",null)){pending=cursor.moveToFirst();}
        if(result.pulled+result.duplicates>=2000||(result.failed==0&&(result.retries>0||pending)))Native4BackgroundSyncWorker.enqueue(getApplicationContext());
       }
       catch(Exception e){android.util.Log.w("Native4Sync","Foreground sync retry pending",e);Native4BackgroundSyncWorker.enqueue(getApplicationContext());}
      }
     }
    }finally{running.set(false);}
   },"native4-active-sync").start();
  }
  return START_STICKY;
 }
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public void onDestroy(){stopped=true;if(connectivity!=null&&networkCallback!=null){try{connectivity.unregisterNetworkCallback(networkCallback);}catch(RuntimeException ignored){}}synchronized(wakeLock){wakeLock.notifyAll();}if(FirebaseAuth.getInstance().getCurrentUser()!=null)Native4BackgroundSyncWorker.enqueue(getApplicationContext());super.onDestroy();}
}
