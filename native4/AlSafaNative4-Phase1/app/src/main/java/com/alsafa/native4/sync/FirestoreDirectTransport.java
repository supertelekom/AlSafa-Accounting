package com.alsafa.native4.sync;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Direct Firebase/Firestore transport. Keeps the persisted operationId idempotent. */
public final class FirestoreDirectTransport implements OutboxSender.Transport {
 private final FirebaseAuth auth;
 private final FirebaseFirestore firestore;
 public FirestoreDirectTransport(){auth=FirebaseAuth.getInstance();firestore=FirebaseFirestore.getInstance();}
 @Override public OutboxSender.Result submit(OutboxSender.Command c) throws OutboxSender.RetryableException,OutboxSender.PermanentException {
  if(!OutboxSender.isTestStore(c.storeId))throw new OutboxSender.PermanentException("رفض storeId غير تجريبي");
  if(auth.getCurrentUser()==null)throw new OutboxSender.PermanentException("يجب تسجيل الدخول قبل إرسال عملية");
  try{
   DocumentReference ref=firestore.collection("native4Stores").document(c.storeId).collection("operations").document(c.operationId);
   DocumentSnapshot existing=Tasks.await(ref.get(),20,TimeUnit.SECONDS);
   if(existing.exists()){
    Long revision=existing.getLong("revision");
    return new OutboxSender.Result(revision==null||revision<1?1:revision,true);
   }
   Map<String,Object> data=new HashMap<>();
   data.put("operationId",c.operationId);data.put("storeId",c.storeId);data.put("sourceDeviceId",c.sourceDeviceId);data.put("kind",c.kind);data.put("recordId",c.recordId);data.put("payload",jsonToMap(new JSONObject(c.payload)));data.put("revision",System.currentTimeMillis());data.put("createdAt",com.google.firebase.firestore.FieldValue.serverTimestamp());data.put("uid",auth.getCurrentUser().getUid());
   Tasks.await(ref.set(data,SetOptions.merge()),20,TimeUnit.SECONDS);
   return new OutboxSender.Result(System.currentTimeMillis(),false);
  }catch(OutboxSender.PermanentException e){throw e;}catch(Exception e){throw new OutboxSender.RetryableException("تعذر الكتابة إلى Firestore: "+e.getClass().getSimpleName());}
 }
 private static Map<String,Object> jsonToMap(JSONObject object)throws Exception{Map<String,Object> map=new HashMap<>();java.util.Iterator<String> keys=object.keys();while(keys.hasNext()){String k=keys.next();Object v=object.get(k);if(v instanceof JSONObject)v=jsonToMap((JSONObject)v);else if(v instanceof org.json.JSONArray){java.util.ArrayList<Object> list=new java.util.ArrayList<>();org.json.JSONArray a=(org.json.JSONArray)v;for(int i=0;i<a.length();i++){Object x=a.get(i);list.add(x instanceof JSONObject?jsonToMap((JSONObject)x):x);}v=list;}map.put(k,v);}return map;}
}
