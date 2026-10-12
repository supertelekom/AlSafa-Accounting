package com.alsafa.native4.core;
import android.content.Context;
/**
 * Both devices have full accounting capabilities.
 * The primary designation is informational (tablet is the preferred closing device),
 * never an authorization or synchronization gate.
 */
public final class Native4DeviceRole {
 private static final String PREFS="native4_device_role";
 private static final String KEY="role";
 public enum Role { TABLET_PRIMARY, PHONE_FULL }
 private Native4DeviceRole(){}
 public static Role get(Context context){
  String value=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,Role.TABLET_PRIMARY.name());
  return "PHONE_FULL".equals(value)||"PHONE_VIEWER".equals(value)?Role.PHONE_FULL:Role.TABLET_PRIMARY;
 }
 public static boolean isViewer(Context context){return false;}
 public static void set(Context context,Role role){
  if(context==null||role==null)throw new IllegalArgumentException("Device role required");
  context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,role.name()).apply();
 }
}
