package com.alsafa.native4.sync;

import java.util.UUID;

/** A durable, idempotent accounting command. It is never a complete database snapshot. */
public final class SyncOperation {
 public enum Kind { SALE, REPAIR, PAYMENT, PURCHASE, EXPENSE, CASH_MOVEMENT, INVENTORY_ADJUSTMENT, PRODUCT_CREATE, PRODUCT_UPDATE, SETTINGS_UPDATE, TEST }
 public final String id, recordId, payload, sourceDeviceId; public final Kind kind; public final long createdAt;
 public SyncOperation(String id,Kind kind,String recordId,String payload,String sourceDeviceId,long createdAt){this.id=id;this.kind=kind;this.recordId=recordId;this.payload=payload;this.sourceDeviceId=sourceDeviceId;this.createdAt=createdAt;}
 public static SyncOperation create(Kind kind,String recordId,String payload,String deviceId){return new SyncOperation(UUID.randomUUID().toString(),kind,recordId,payload,deviceId,System.currentTimeMillis());}
 public void validate(){
  if(blank(id)||!id.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"))throw new IllegalArgumentException("operationId غير صالح"); if(kind==null)throw new IllegalArgumentException("نوع العملية مطلوب");
  if(blank(recordId))throw new IllegalArgumentException("مرجع العملية مطلوب"); if(blank(payload))throw new IllegalArgumentException("بيانات العملية مطلوبة"); if(blank(sourceDeviceId))throw new IllegalArgumentException("معرف الجهاز مطلوب");
 }
 private static boolean blank(String value){return value==null||value.trim().isEmpty();}
}
