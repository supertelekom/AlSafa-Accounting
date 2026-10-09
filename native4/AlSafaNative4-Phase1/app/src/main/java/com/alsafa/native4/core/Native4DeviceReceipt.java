package com.alsafa.native4.core;
import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
/** Publishes only a checkpoint already committed in SQLite, never a speculative receipt. */
public final class Native4DeviceReceipt {
 private Native4DeviceReceipt(){}
 public static String deviceId(Context context){
  SharedPreferences p=context.getApplicationContext().getSharedPreferences("native4_device_identity",Context.MODE_PRIVATE);
  synchronized(Native4DeviceReceipt.class){
   String id=p.getString("device_id",null);
   if(id==null){id=UUID.randomUUID().toString();if(!p.edit().putString("device_id",id).commit())throw new IllegalStateException("تعذر حفظ معرف الجهاز");}
   return id;
  }
 }
 public static void publish(Context context,long appliedRevision)throws Exception{
  if(appliedRevision<=0)return;
  String id=deviceId(context);
  Map<String,Object> data=new HashMap<>();
  data.put("deviceId",id);
  data.put("appliedRevision",appliedRevision);
  data.put("updatedAt",System.currentTimeMillis());
  Tasks.await(FirebaseFirestore.getInstance().collection("native4_device_receipts").document(id).set(data),20,TimeUnit.SECONDS);
 }
}
