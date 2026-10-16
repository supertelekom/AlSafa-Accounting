package com.alsafa.native4.core;
import android.content.Context;

/** Tablet-first daily upload: sends committed local operations to Firebase; remote restore is separate. */
public final class Native4SyncCoordinator {
 /** Serializes uploads so concurrent UI and background requests cannot push together. */
 private static final Object CYCLE_LOCK = new Object();
 public static final class Result {
  public final int pushed,retries,failed,pulled,duplicates;public final long checkpoint;
  Result(int p,int r,int f,int in,int d,long c){pushed=p;retries=r;failed=f;pulled=in;duplicates=d;checkpoint=c;}
 }
 private final Native4SyncEngine sender;
 public Native4SyncCoordinator(CoreDb db){this(db,null);}
 public Native4SyncCoordinator(CoreDb db,Context context){Native4FirestoreTransport transport=new Native4FirestoreTransport();sender=new Native4SyncEngine(db,transport);}
 public Result syncOnce()throws Exception{return syncOnce(null);}
 public Result syncOnce(Native4SyncEngine.Progress progress)throws Exception{ synchronized(CYCLE_LOCK){ Native4SyncEngine.Result out=sender.pushPending(progress);return new Result(out.confirmed,out.retries,out.failed,0,0,0); } }
 public Result syncOnce(Native4SyncEngine.Progress progress,Runnable beforePull)throws Exception{return syncOnce(progress,beforePull,null);}
 public Result syncOnce(Native4SyncEngine.Progress progress,Runnable beforePull,Native4SyncReceiver.Progress receiveProgress)throws Exception{ synchronized(CYCLE_LOCK){ Native4SyncEngine.Result out=sender.pushPending(progress);if(beforePull!=null){try{beforePull.run();}catch(RuntimeException callbackError){android.util.Log.w("Native4Sync","UI progress callback failed; upload continues",callbackError);}}return new Result(out.confirmed,out.retries,out.failed,0,0,0); } }
}
