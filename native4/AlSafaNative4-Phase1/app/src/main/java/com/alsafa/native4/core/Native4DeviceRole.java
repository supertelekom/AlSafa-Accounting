package com.alsafa.native4.core;
import android.content.Context;
/** Device role is local configuration; server-side write authorization must be enforced separately. */
public final class Native4DeviceRole {
 private static final String PREFS="native4_device_role";
 private static final String KEY="role";
 public enum Role { TABLET_PRIMARY, PHONE_VIEWER }
 private Native4DeviceRole(){}
 public static Role get(Context context){
  String value=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,Role.TABLET_PRIMARY.name());
  return Role.PHONE_VIEWER.name().equals(value)?Role.PHONE_VIEWER:Role.TABLET_PRIMARY;
 }
 public static boolean isViewer(Context context){return get(context)==Role.PHONE_VIEWER;}
 public static void set(Context context,Role role){
  if(context==null||role==null)throw new IllegalArgumentException("Device role required");
  context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,role.name()).apply();
 }
}
