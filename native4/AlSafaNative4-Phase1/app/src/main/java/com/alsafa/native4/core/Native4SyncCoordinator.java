package com.alsafa.native4.core;
import android.content.Context;

/** One safe Native4 sync cycle: push local operations, then pull/apply remote operations. */
public final class Native4SyncCoordinator {
 private static final Object CYCLE_LOCK = new Object();
 public static final class Result {
  public final int pushed,retries,failed,pulled,duplicates;public final long checkpoint;
  Result(int p,int r,int f,int in,int d,long c){pushed=p;retries=r;failed=f;pulled=in;duplicates=d;checkpoint=c;}
 }
 private final Native4SyncEngine sender;private final Native4SyncReceiver receiver;private final Context receiptContext;
 public Native4SyncCoordinator(CoreDb db){this(db,null);}
 public Native4SyncCoordinator(CoreDb db,Context context){receiptContext=context==null?null:context.getApplicationContext();Native4FirestoreTransport transport=new Native4FirestoreTransport();sender=new Native4SyncEngine(db,transport);receiver=new Native4SyncReceiver(db,transport,new Native4OperationApplier());}
 public Result syncOnce()throws Exception{return syncOnce(null);}
 public Result syncOnce(Native4SyncEngine.Progress progress)throws Exception{return syncOnce(progress,null);}
 public Result syncOnce(Native4SyncEngine.Progress progress,Runnable beforePull)throws Exception{return syncOnce(progress,beforePull,null);}
 public Result syncOnce(Native4SyncEngine.Progress progress,Runnable beforePull,Native4SyncReceiver.Progress receiveProgress)throws Exception{ synchronized(CYCLE_LOCK){ Native4SyncEngine.Result out=receiptContext!=null&&Native4DeviceRole.isViewer(receiptContext)?new Native4SyncEngine.Result(0,0,0):sender.pushPending(progress);if(beforePull!=null){try{beforePull.run();}catch(RuntimeException callbackError){android.util.Log.w("Native4Sync","UI progress callback failed; incoming sync continues",callbackError);}}Native4SyncReceiver.Result in=receiver.pullOnce(receiveProgress);if(receiptContext!=null){try{Native4DeviceReceipt.publish(receiptContext,in.checkpoint);}catch(Exception receiptError){android.util.Log.w("Native4Sync","Receipt publication deferred; applied data remains committed",receiptError);}}return new Result(out.confirmed,out.retries,out.failed,in.applied,in.duplicates,in.checkpoint); } }
}
