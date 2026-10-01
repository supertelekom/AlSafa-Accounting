package com.alsafa.native4.core;

import android.content.Context;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/** Schedules one unique durable sync job. Repeated requests do not create duplicate workers. */
public final class Native4BackgroundSync {
 private static final String UNIQUE_WORK="alsafa-native4-sync";
 private Native4BackgroundSync(){}
 public static void request(Context context){
  Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
  OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(Native4SyncWorker.class)
   .setConstraints(constraints)
   .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,10,TimeUnit.SECONDS)
   .build();
  WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(UNIQUE_WORK,ExistingWorkPolicy.KEEP,work);
 }
}
