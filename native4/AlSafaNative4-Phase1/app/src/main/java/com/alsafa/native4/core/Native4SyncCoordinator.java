package com.alsafa.native4.core;

/** One safe Native4 sync cycle: push local operations, then pull/apply remote operations. */
public final class Native4SyncCoordinator {
 public static final class Result {
  public final int pushed,retries,failed,pulled,duplicates;public final long checkpoint;
  Result(int p,int r,int f,int in,int d,long c){pushed=p;retries=r;failed=f;pulled=in;duplicates=d;checkpoint=c;}
 }
 private final Native4SyncEngine sender;private final Native4SyncReceiver receiver;
 public Native4SyncCoordinator(CoreDb db){Native4FirestoreTransport transport=new Native4FirestoreTransport();sender=new Native4SyncEngine(db,transport);receiver=new Native4SyncReceiver(db,transport,new Native4OperationApplier());}
 public Result syncOnce()throws Exception{Native4SyncEngine.Result out=sender.pushPending();Native4SyncReceiver.Result in=receiver.pullOnce();return new Result(out.confirmed,out.retries,out.failed,in.applied,in.duplicates,in.checkpoint);}
}
