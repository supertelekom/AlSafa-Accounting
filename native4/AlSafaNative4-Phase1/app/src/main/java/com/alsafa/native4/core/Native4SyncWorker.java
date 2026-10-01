package com.alsafa.native4.core;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.firebase.auth.FirebaseAuth;

/** Durable background sync. Local Outbox remains source of truth; failures are retried, never dropped. */
public final class Native4SyncWorker extends Worker {
 public Native4SyncWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
 @NonNull @Override public Result doWork(){
  if(FirebaseAuth.getInstance().getCurrentUser()==null)return Result.retry();
  CoreDb db=new CoreDb(getApplicationContext());
  try{
   Native4SyncCoordinator.Result r=new Native4SyncCoordinator(db).syncOnce();
   if(r.failed>0||r.retries>0)return Result.retry();
   return Result.success();
  }catch(Exception e){return Result.retry();}
 }
}
