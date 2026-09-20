package com.alsafa.native4.sync;

import java.util.UUID;

/** A durable, idempotent accounting command. It is never a complete database snapshot. */
public final class SyncOperation {
 public enum Kind { SALE, REPAIR, PAYMENT, PURCHASE, EXPENSE, CASH_MOVEMENT, INVENTORY_ADJUSTMENT, PRODUCT_CREATE, PRODUCT_UPDATE, SETTINGS_UPDATE, TEST }
 public final String id, recordId, payload, sourceDeviceId; public final Kind kind; public final long createdAt;
 private SyncOperation(String id,Kind kind,String recordId,String payload,String sourceDeviceId,long createdAt){this.id=id;this.kind=kind;this.recordId=recordId;this.payload=payload;this.sourceDeviceId=sourceDeviceId;this.createdAt=createdAt;}
 public static SyncOperation create(Kind kind,String recordId,String payload,String deviceId){return new SyncOperation(UUID.randomUUID().toString(),kind,recordId,payload,deviceId,System.currentTimeMillis());}
 public void validate(){
  if(blank(id))throw new IllegalArgumentException("operationId مطلوب"); if(kind==null)throw new IllegalArgumentException("نوع العملية مطلوب");
  if(blank(recordId))throw new IllegalArgumentException("مرجع العملية مطلوب"); if(blank(payload))throw new IllegalArgumentException("بيانات العملية مطلوبة"); if(blank(sourceDeviceId))throw new IllegalArgumentException("معرف الجهاز مطلوب");
 }
 private static boolean blank(String value){return value==null||value.trim().isEmpty();}
}
