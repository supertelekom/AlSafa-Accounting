package com.alsafa.native4.sync;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Authenticated Firestore REST transport.
 * operationId is the document id, so a retry overwrites/updates the same remote operation and cannot duplicate it.
 * REST is used deliberately here because Firebase Auth succeeds on the device while the Android Firestore SDK write
 * channel can remain pending until timeout on the tested network.
 */
public final class FirestoreDirectTransport implements OutboxSender.Transport {
 private static final String PROJECT_ID="alsafa-native";
 private final FirebaseAuth auth;
 public FirestoreDirectTransport(){auth=FirebaseAuth.getInstance();}

 @Override public OutboxSender.Result submit(OutboxSender.Command c) throws OutboxSender.RetryableException,OutboxSender.PermanentException {
  if(!OutboxSender.isTestStore(c.storeId))throw new OutboxSender.PermanentException("رفض storeId غير تجريبي");
  FirebaseUser user=auth.getCurrentUser();
  if(user==null)throw new OutboxSender.PermanentException("يجب تسجيل الدخول قبل إرسال عملية");
  final String token;
  try{
   GetTokenResult tokenResult=Tasks.await(user.getIdToken(true),15,TimeUnit.SECONDS);
   token=tokenResult.getToken();
   if(token==null||token.trim().isEmpty())throw new IllegalStateException("Firebase ID token فارغ");
  }catch(Exception e){throw retryable("AUTH_TOKEN",e);}

  HttpURLConnection connection=null;
  try{
   long revision=System.currentTimeMillis();
   String documentUrl="https://firestore.googleapis.com/v1/projects/"+PROJECT_ID+"/databases/(default)/documents/native4Stores/"+enc(c.storeId)+"/operations/"+enc(c.operationId);
   connection=(HttpURLConnection)new URL(documentUrl).openConnection();
   connection.setRequestMethod("PATCH");
   connection.setConnectTimeout(15000);
   connection.setReadTimeout(20000);
   connection.setDoOutput(true);
   connection.setRequestProperty("Authorization","Bearer "+token);
   connection.setRequestProperty("Content-Type","application/json; charset=UTF-8");
   connection.setRequestProperty("Accept","application/json");

   JSONObject fields=new JSONObject();
   putString(fields,"operationId",c.operationId);putString(fields,"storeId",c.storeId);putString(fields,"sourceDeviceId",c.sourceDeviceId);putString(fields,"kind",c.kind);putString(fields,"recordId",c.recordId);putString(fields,"uid",user.getUid());putInteger(fields,"revision",revision);putMap(fields,"payload",new JSONObject(c.payload));
   // Client timestamp is sufficient for the outbox acknowledgement document; revision remains authoritative.
   putTimestamp(fields,"lastSubmittedAt",java.time.Instant.now().toString());
   JSONObject body=new JSONObject().put("fields",fields);
   byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
   connection.setFixedLengthStreamingMode(bytes.length);
   try(OutputStream os=connection.getOutputStream()){os.write(bytes);}

   int code=connection.getResponseCode();
   String response=read(code>=200&&code<300?connection.getInputStream():connection.getErrorStream());
   if(code>=200&&code<300)return new OutboxSender.Result(revision,false);
   String detail="HTTP "+code+(response.isEmpty()?"":" "+response);
   if(code==400||code==401||code==403||code==404)throw new OutboxSender.PermanentException("FIRESTORE_REST "+detail);
   throw new OutboxSender.RetryableException("FIRESTORE_REST "+detail);
  }catch(OutboxSender.PermanentException|OutboxSender.RetryableException e){throw e;
  }catch(Exception e){throw retryable("FIRESTORE_REST",e);
  }finally{if(connection!=null)connection.disconnect();}
 }

 private static String enc(String s)throws Exception{return java.net.URLEncoder.encode(s,"UTF-8").replace("+","%20");}
 private static String read(InputStream in)throws Exception{if(in==null)return "";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){if(b.length()>0)b.append(' ');b.append(line);}}return b.toString();}
 private static void putString(JSONObject f,String k,String v)throws Exception{f.put(k,new JSONObject().put("stringValue",v==null?"":v));}
 private static void putInteger(JSONObject f,String k,long v)throws Exception{f.put(k,new JSONObject().put("integerValue",Long.toString(v)));}
 private static void putTimestamp(JSONObject f,String k,String v)throws Exception{f.put(k,new JSONObject().put("timestampValue",v));}
 private static void putMap(JSONObject f,String k,JSONObject v)throws Exception{f.put(k,new JSONObject().put("mapValue",new JSONObject().put("fields",toFields(v))));}
 private static JSONObject toFields(JSONObject object)throws Exception{JSONObject fields=new JSONObject();Iterator<String> keys=object.keys();while(keys.hasNext()){String k=keys.next();fields.put(k,toValue(object.get(k)));}return fields;}
 private static JSONObject toValue(Object v)throws Exception{
  if(v==null||v==JSONObject.NULL)return new JSONObject().put("nullValue",null);
  if(v instanceof JSONObject)return new JSONObject().put("mapValue",new JSONObject().put("fields",toFields((JSONObject)v)));
  if(v instanceof JSONArray){JSONArray src=(JSONArray)v,out=new JSONArray();for(int i=0;i<src.length();i++)out.put(toValue(src.get(i)));return new JSONObject().put("arrayValue",new JSONObject().put("values",out));}
  if(v instanceof Boolean)return new JSONObject().put("booleanValue",v);
  if(v instanceof Byte||v instanceof Short||v instanceof Integer||v instanceof Long)return new JSONObject().put("integerValue",String.valueOf(v));
  if(v instanceof Number)return new JSONObject().put("doubleValue",((Number)v).doubleValue());
  return new JSONObject().put("stringValue",String.valueOf(v));
 }
 private static OutboxSender.RetryableException retryable(String stage,Exception error){Throwable root=error;while(root.getCause()!=null&&root.getCause()!=root)root=root.getCause();if(root instanceof TimeoutException||root instanceof java.net.SocketTimeoutException)return new OutboxSender.RetryableException(stage+" TIMEOUT: "+safe(root.getMessage()));return new OutboxSender.RetryableException(stage+" "+root.getClass().getSimpleName()+": "+safe(root.getMessage()));}
 private static String safe(String value){return value==null||value.trim().isEmpty()?"بلا تفاصيل":value.trim();}
}
