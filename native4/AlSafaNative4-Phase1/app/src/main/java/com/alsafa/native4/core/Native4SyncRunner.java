package com.alsafa.native4.core;

import android.app.Activity;
import com.google.firebase.auth.FirebaseAuth;

/** UI bridge for the new Native4-only sync path. Alpha sync is intentionally not called here. */
public final class Native4SyncRunner {
 public interface Callback { void done(Native4SyncCoordinator.Result result); void failed(Exception error); }
 private Native4SyncRunner(){}
 public static void run(Activity activity,CoreDb db,Callback callback){
  if(activity==null||db==null||callback==null)throw new IllegalArgumentException("Sync runner dependencies مطلوبة");
  if(FirebaseAuth.getInstance().getCurrentUser()==null){callback.failed(new IllegalStateException("سجل الدخول أولاً؛ لم يتم إرسال شيء"));return;}
  new Thread(()->{
   try{Native4SyncCoordinator.Result r=new Native4SyncCoordinator(db).syncOnce();activity.runOnUiThread(()->callback.done(r));}
   catch(Exception e){activity.runOnUiThread(()->callback.failed(e));}
  },"native4-sync").start();
 }
 public static String summary(Native4SyncCoordinator.Result r){
  return "✓ المزامنة الجديدة — أرسل: "+r.pushed+" | استقبل: "+r.pulled+" | مكرر آمن: "+r.duplicates+" | إعادة محاولة: "+r.retries+" | فشل: "+r.failed+" | checkpoint: "+r.checkpoint;
 }
}
