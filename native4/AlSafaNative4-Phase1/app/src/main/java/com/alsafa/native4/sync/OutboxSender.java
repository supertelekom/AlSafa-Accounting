package com.alsafa.native4.sync;

import com.alsafa.native4.Native4Safety;
import java.util.List;

/**
 * Retry-safe sender orchestration. The transport is injected deliberately so the
 * local-core APK cannot accidentally talk to production. It always sends the
 * exact persisted operationId/payload; it never rebuilds a SALE.
 */
public final class OutboxSender {
 public interface Transport {
  Result submit(Command command) throws RetryableException, PermanentException;
 }
 public static final class Command {
  public final String operationId,storeId,sourceDeviceId,kind,recordId,payload;
  Command(String operationId,String storeId,String sourceDeviceId,String kind,String recordId,String payload){this.operationId=operationId;this.storeId=storeId;this.sourceDeviceId=sourceDeviceId;this.kind=kind;this.recordId=recordId;this.payload=payload;}
 }
 public static final class Result { public final long revision; public final boolean duplicate; public Result(long revision,boolean duplicate){if(revision<1)throw new IllegalArgumentException("revision غير صالح");this.revision=revision;this.duplicate=duplicate;} }
 public static final class RetryableException extends Exception { public RetryableException(String m){super(m);} }
 public static final class PermanentException extends Exception { public PermanentException(String m){super(m);} }
 private final OutboxDb db; private final Transport transport; private final String storeId;
 public OutboxSender(OutboxDb db,Transport transport,String storeId){if(db==null||transport==null)throw new IllegalArgumentException("Sender dependencies مطلوبة");if(!isTestStore(storeId))throw new IllegalArgumentException("OutboxSender محصور بمتجر اختبار Native4 معزول");this.db=db;this.transport=transport;this.storeId=storeId;}
 public int drainOnce(){int confirmed=0;List<OutboxDb.Item> items=db.pending();for(OutboxDb.Item item:items){db.markSending(item.id);Command command=new Command(item.id,storeId,item.sourceDeviceId,item.kind,item.recordId,item.payload);try{Result result=transport.submit(command);db.confirm(item.id,result.revision);confirmed++;}catch(RetryableException e){db.retryLater(item.id,e.getMessage());}catch(PermanentException e){db.failPermanent(item.id,e.getMessage());}catch(RuntimeException e){db.retryLater(item.id,"تعذر الإرسال: "+e.getClass().getSimpleName());}}return confirmed;}
 public static boolean isTestStore(String value){return Native4Safety.isIsolatedStore(value);}
}
