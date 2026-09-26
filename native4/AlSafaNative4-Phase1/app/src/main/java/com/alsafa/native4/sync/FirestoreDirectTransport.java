package com.alsafa.native4.sync;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Direct Firebase/Firestore transport. The persisted operationId is the document id, so retries cannot create duplicates. */
public final class FirestoreDirectTransport implements OutboxSender.Transport {
 private final FirebaseAuth auth;
 private final FirebaseFirestore firestore;
 public FirestoreDirectTransport(){auth=FirebaseAuth.getInstance();firestore=FirebaseFirestore.getInstance();}
 @Override public OutboxSender.Result submit(OutboxSender.Command c) throws OutboxSender.RetryableException,OutboxSender.PermanentException {
  if(!OutboxSender.isTestStore(c.storeId))throw new OutboxSender.PermanentException("رفض storeId غير تجريبي");
  FirebaseUser user=auth.getCurrentUser();
  if(user==null)throw new OutboxSender.PermanentException("يجب تسجيل الدخول قبل إرسال عملية");
  // Preflight deliberately separates Auth/network failures from a Firestore write timeout.
  try{Tasks.await(user.getIdToken(true),15,TimeUnit.SECONDS);}catch(Exception e){throw retryable("AUTH_TOKEN",e);}
  try{Tasks.await(firestore.enableNetwork(),10,TimeUnit.SECONDS);}catch(Exception e){throw retryable("FIRESTORE_NETWORK",e);}
  try{
   DocumentReference ref=firestore.collection("native4Stores").document(c.storeId).collection("operations").document(c.operationId);
   long revision=System.currentTimeMillis();
   Map<String,Object> data=new HashMap<>();
   data.put("operationId",c.operationId);data.put("storeId",c.storeId);data.put("sourceDeviceId",c.sourceDeviceId);data.put("kind",c.kind);data.put("recordId",c.recordId);data.put("payload",jsonToMap(new JSONObject(c.payload)));data.put("revision",revision);data.put("lastSubmittedAt",com.google.firebase.firestore.FieldValue.serverTimestamp());data.put("uid",user.getUid());
   // No read-before-write: deterministic operationId makes retries idempotent.
   Tasks.await(ref.set(data,SetOptions.merge()),30,TimeUnit.SECONDS);
   return new OutboxSender.Result(revision,false);
  }catch(Exception e){throw retryable("FIRESTORE_WRITE",e);}
 }
 private static OutboxSender.RetryableException retryable(String stage,Exception error){
  Throwable root=rootCause(error);
  if(root instanceof FirebaseFirestoreException){FirebaseFirestoreException f=(FirebaseFirestoreException)root;return new OutboxSender.RetryableException(stage+" Firestore "+f.getCode().name()+": "+safe(f.getMessage()));}
  if(root instanceof TimeoutException)return new OutboxSender.RetryableException(stage+" TIMEOUT: "+safe(root.getMessage()));
  return new OutboxSender.RetryableException(stage+" "+root.getClass().getSimpleName()+": "+safe(root.getMessage()));
 }
 private static Throwable rootCause(Throwable error){Throwable current=error;if(current instanceof ExecutionException&&current.getCause()!=null)current=current.getCause();while(current.getCause()!=null&&current.getCause()!=current)current=current.getCause();return current;}
 private static String safe(String value){return value==null||value.trim().isEmpty()?"بلا تفاصيل":value.trim();}
 private static Map<String,Object> jsonToMap(JSONObject object)throws Exception{Map<String,Object> map=new HashMap<>();java.util.Iterator<String> keys=object.keys();while(keys.hasNext()){String k=keys.next();Object v=object.get(k);if(v instanceof JSONObject)v=jsonToMap((JSONObject)v);else if(v instanceof org.json.JSONArray){java.util.ArrayList<Object> list=new java.util.ArrayList<>();org.json.JSONArray a=(org.json.JSONArray)v;for(int i=0;i<a.length();i++){Object x=a.get(i);list.add(x instanceof JSONObject?jsonToMap((JSONObject)x):x);}v=list;}map.put(k,v);}return map;}
}
