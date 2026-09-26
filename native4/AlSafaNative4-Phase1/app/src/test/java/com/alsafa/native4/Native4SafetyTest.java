package com.alsafa.native4;

import org.junit.Test;
import static org.junit.Assert.*;

public class Native4SafetyTest {
 @Test public void acceptsOnlyNative4TestPrefix(){
  assertFalse(Native4Safety.isIsolatedStore("test-native4"));
  assertTrue(Native4Safety.isIsolatedStore("test-native4-phone"));
  assertTrue(Native4Safety.isIsolatedStore("test-native4-tablet"));
  assertFalse(Native4Safety.isIsolatedStore("alsafa"));
  assertFalse(Native4Safety.isIsolatedStore("native4Stores"));
  assertFalse(Native4Safety.isIsolatedStore("test-native40-phone"));
  assertFalse(Native4Safety.isIsolatedStore(""));
  assertFalse(Native4Safety.isIsolatedStore(null));
 }
 @Test(expected=IllegalStateException.class) public void productionLikeStoreIsRejected(){Native4Safety.requireIsolatedStore("alsafa");}
 @Test(expected=IllegalStateException.class) public void bareTestNamespaceIsRejected(){Native4Safety.requireIsolatedStore("test-native4");}
}
