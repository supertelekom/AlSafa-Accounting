package com.alsafa.native4.sync;

import static org.junit.Assert.*;
import org.junit.Test;

/** Pure JVM safety-policy tests; SQLite state transitions are covered separately on Android/emulator. */
public class OutboxSenderPolicyTest {
 @Test public void acceptsOnlyExplicitNative4IsolatedStores(){
  assertTrue(OutboxSender.isTestStore("test-native4-android-alpha"));
  assertTrue(OutboxSender.isTestStore("test-native4-phone"));
  assertFalse(OutboxSender.isTestStore("test-native4"));
  assertFalse(OutboxSender.isTestStore("test-alsafa"));
  assertFalse(OutboxSender.isTestStore("alsafa-test"));
  assertFalse(OutboxSender.isTestStore("alsafa-live"));
  assertFalse(OutboxSender.isTestStore("test"));
  assertFalse(OutboxSender.isTestStore(null));
 }
 @Test public void confirmedResultRequiresPositiveRevision(){
  assertEquals(7,new OutboxSender.Result(7,false).revision);
  try { new OutboxSender.Result(0,false); fail("zero revision must fail"); } catch(IllegalArgumentException expected) { }
 }
 @Test public void commandKeepsExactPersistedIdentityAndPayload(){
  String id="2b0b38b8-4dc1-4f4d-8e27-8a7b88c1ef14";
  String payload="{\"total\":25}";
  OutboxSender.Command c=new OutboxSender.Command(id,"test-native4-phone","phone-1","SALE","sale-1",payload);
  assertSame(id,c.operationId);
  assertSame(payload,c.payload);
  assertEquals("sale-1",c.recordId);
  assertEquals("phone-1",c.sourceDeviceId);
 }
}
