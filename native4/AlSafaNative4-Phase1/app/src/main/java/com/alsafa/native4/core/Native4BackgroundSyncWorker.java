package com.alsafa.native4.core;

import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;
import com.google.firebase.auth.FirebaseAuth;
import java.util.concurrent.TimeUnit;

/** Durable, network-aware sync that survives Activity navigation and process death. */
public final class Native4BackgroundSyncWorker extends Worker {
 private static final String UNIQUE_NAME="native4-background-sync";
 public Native4BackgroundSyncWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
 public static void cancelLegacyPeriodic(Context context){WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork("native4-periodic-sync");}
 public static void enqueue(Context context){
  Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
  OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(Native4BackgroundSyncWorker.class)
   .setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build();
  WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(UNIQUE_NAME,ExistingWorkPolicy.KEEP,work);
 }
 @NonNull @Override public Result doWork(){
  if(FirebaseAuth.getInstance().getCurrentUser()==null)return Result.success();
  try{
   try(CoreDb db=new CoreDb(getApplicationContext())){
    Native4SyncCoordinator.Result result=new Native4SyncCoordinator(db,getApplicationContext()).syncOnce();
    boolean pending;
    try(Cursor cursor=db.db().rawQuery("SELECT 1 FROM outbox WHERE state IN ('PENDING','RETRY','SENDING') LIMIT 1",null)){pending=cursor.moveToFirst();}
    if(result.failed>0){android.util.Log.w("Native4Sync","Unresolved permanent upload failure; waiting for safe in-app recovery instead of retrying forever");return result.pulled+result.duplicates>=2000?Result.retry():Result.success();}
    return result.retries>0||pending||result.pulled+result.duplicates>=2000?Result.retry():Result.success();
   }
  }catch(Exception e){android.util.Log.w("Native4Sync","Background sync will retry",e);return Result.retry();}
 }
}
