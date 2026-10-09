package com.alsafa.native4.core;
import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
/** Publishes only a checkpoint already committed in SQLite, never a speculative receipt. */
public final class Native4DeviceReceipt {
 public static QuerySnapshot readReceipts()throws Exception{return Tasks.await(FirebaseFirestore.getInstance().collection("native4_device_receipts").get(Source.SERVER),20,TimeUnit.SECONDS);}
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
  DocumentReference ref=FirebaseFirestore.getInstance().collection("native4_device_receipts").document(id);
  Tasks.await(FirebaseFirestore.getInstance().runTransaction(tx->{
   DocumentSnapshot previous=tx.get(ref);
   Long old=previous.getLong("appliedRevision");
   if(old!=null&&old>=appliedRevision)return null;
   tx.set(ref,data);
   return null;
  }),20,TimeUnit.SECONDS);
 }
}
