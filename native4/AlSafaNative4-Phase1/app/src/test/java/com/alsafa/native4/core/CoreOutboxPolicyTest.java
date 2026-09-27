package com.alsafa.native4.core;

import static org.junit.Assert.*;
import com.alsafa.native4.sync.OutboxSender;
import org.junit.Test;

/** Pure JVM guards for the Core outbox transport contract. SQLite transitions remain Android/instrumented territory. */
public class CoreOutboxPolicyTest {
 @Test public void coreUsesSamePersistedIdentityContractAsRemoteOperation(){
  String operationId="core-close-123";
  String payload="{\"businessDay\":\"2026-09-27\",\"carryUSD\":100}";
  OutboxSender.Command c=new OutboxSender.Command(operationId,"test-native4-android-alpha","native4-android-alpha","DAILY_CLOSING","closing-123",payload);
  assertEquals(operationId,c.operationId);
  assertEquals("DAILY_CLOSING",c.kind);
  assertEquals("closing-123",c.recordId);
  assertEquals(payload,c.payload);
  assertEquals("native4-android-alpha",c.sourceDeviceId);
 }

 @Test public void workDayOpenKeepsItsOriginalOperationIdOnRetryContract(){
  String operationId="core-open-456";
  OutboxSender.Command first=new OutboxSender.Command(operationId,"test-native4-android-alpha","native4-android-alpha","WORK_DAY_OPEN","2026-09-28","{\"openingUSD\":100}");
  OutboxSender.Command retry=new OutboxSender.Command(operationId,"test-native4-android-alpha","native4-android-alpha","WORK_DAY_OPEN","2026-09-28",first.payload);
  assertEquals(first.operationId,retry.operationId);
  assertEquals(first.recordId,retry.recordId);
  assertEquals(first.payload,retry.payload);
 }

 @Test public void coreTransportRemainsRestrictedToExplicitIsolatedStore(){
  assertTrue(OutboxSender.isTestStore("test-native4-android-alpha"));
  assertFalse(OutboxSender.isTestStore("alsafa-live"));
  assertFalse(OutboxSender.isTestStore("test-native4"));
 }
}
