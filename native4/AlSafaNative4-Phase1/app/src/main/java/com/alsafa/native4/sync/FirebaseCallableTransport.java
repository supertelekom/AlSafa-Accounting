package com.alsafa.native4.sync;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.google.firebase.functions.HttpsCallableResult;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Authenticated Firebase Callable transport. Hard-restricted to test stores. */
public final class FirebaseCallableTransport implements OutboxSender.Transport {
 private final FirebaseAuth auth;
 private final FirebaseFunctions functions;
 public FirebaseCallableTransport(){
  auth=FirebaseAuth.getInstance();
  functions=FirebaseFunctions.getInstance("europe-west1");
 }
 @Override public OutboxSender.Result submit(OutboxSender.Command c) throws OutboxSender.RetryableException, OutboxSender.PermanentException {
  if(!OutboxSender.isTestStore(c.storeId))throw new OutboxSender.PermanentException("رفض storeId غير تجريبي");
  if(auth.getCurrentUser()==null)throw new OutboxSender.PermanentException("يجب تسجيل الدخول قبل إرسال عملية");
  try{
   Map<String,Object> data=new HashMap<>();data.put("operationId",c.operationId);data.put("storeId",c.storeId);data.put("sourceDeviceId",c.sourceDeviceId);data.put("kind",c.kind);data.put("recordId",c.recordId);data.put("payload",jsonToMap(new JSONObject(c.payload)));
   HttpsCallableResult result=Tasks.await(functions.getHttpsCallable("submitCommand").call(data),20,TimeUnit.SECONDS);
   if(!(result.getData() instanceof Map))throw new OutboxSender.RetryableException("رد Callable غير صالح");
   Map<?,?> response=(Map<?,?>)result.getData();long revision=number(response.get("revision"));boolean duplicate=Boolean.TRUE.equals(response.get("duplicate"));if(revision<1)throw new OutboxSender.RetryableException("رد Callable بلا revision صالح");return new OutboxSender.Result(revision,duplicate);
  }catch(OutboxSender.RetryableException|OutboxSender.PermanentException e){throw e;}
  catch(Exception e){
   Throwable cause=e.getCause()!=null?e.getCause():e;
   if(cause instanceof FirebaseFunctionsException){FirebaseFunctionsException.Code code=((FirebaseFunctionsException)cause).getCode();switch(code){case INVALID_ARGUMENT:case FAILED_PRECONDITION:case ALREADY_EXISTS:case PERMISSION_DENIED:case UNAUTHENTICATED:throw new OutboxSender.PermanentException("Callable "+code.name());default:throw new OutboxSender.RetryableException("Callable "+code.name());}}
   throw new OutboxSender.RetryableException("تعذر الاتصال بالـCallable: "+cause.getClass().getSimpleName());
  }
 }
 private static long number(Object value){return value instanceof Number?((Number)value).longValue():0;}
 private static Map<String,Object> jsonToMap(JSONObject object)throws Exception{Map<String,Object> map=new HashMap<>();java.util.Iterator<String> keys=object.keys();while(keys.hasNext()){String k=keys.next();Object v=object.get(k);if(v instanceof JSONObject)v=jsonToMap((JSONObject)v);else if(v instanceof org.json.JSONArray){java.util.ArrayList<Object> list=new java.util.ArrayList<>();org.json.JSONArray a=(org.json.JSONArray)v;for(int i=0;i<a.length();i++){Object x=a.get(i);list.add(x instanceof JSONObject?jsonToMap((JSONObject)x):x);}v=list;}map.put(k,v);}return map;}
}
