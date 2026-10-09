package com.alsafa.native4.core;

import android.content.Context;
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
   CoreDb db=new CoreDb(getApplicationContext());
   Native4SyncCoordinator.Result result=new Native4SyncCoordinator(db,getApplicationContext()).syncOnce();
   return result.retries>0?Result.retry():Result.success();
  }catch(Exception e){android.util.Log.w("Native4Sync","Background sync will retry",e);return Result.retry();}
 }
}
