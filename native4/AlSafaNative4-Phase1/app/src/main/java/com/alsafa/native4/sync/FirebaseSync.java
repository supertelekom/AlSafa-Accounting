package com.alsafa.native4.sync;

/**
 * Deliberately inert compatibility boundary.
 * Native 4 local-core builds must not contain Firebase imports, credentials,
 * network code, background listeners, or automatic data transfer.
 */
public final class FirebaseSync {
  private FirebaseSync() {}
  public static boolean isAvailable() { return false; }
}
