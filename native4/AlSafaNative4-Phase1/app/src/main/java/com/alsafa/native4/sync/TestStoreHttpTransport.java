package com.alsafa.native4.sync;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** HTTP transport for a dedicated Native 4 test endpoint only. No production store is accepted. */
public final class TestStoreHttpTransport implements OutboxSender.Transport {
 private final URL endpoint;
 public TestStoreHttpTransport(String endpointUrl) {
  try {
   endpoint = new URL(endpointUrl);
   if (!"https".equalsIgnoreCase(endpoint.getProtocol())) throw new IllegalArgumentException("endpoint يجب أن يكون HTTPS");
  } catch (Exception e) { throw new IllegalArgumentException("عنوان endpoint التجريبي غير صالح", e); }
 }
 @Override public OutboxSender.Result submit(OutboxSender.Command c) throws OutboxSender.RetryableException, OutboxSender.PermanentException {
  if (!OutboxSender.isTestStore(c.storeId)) throw new OutboxSender.PermanentException("رفض storeId غير تجريبي");
  HttpURLConnection conn=null;
  try {
   JSONObject body=new JSONObject();body.put("operationId",c.operationId);body.put("storeId",c.storeId);body.put("sourceDeviceId",c.sourceDeviceId);body.put("kind",c.kind);body.put("recordId",c.recordId);body.put("payload",new JSONObject(c.payload));
   conn=(HttpURLConnection)endpoint.openConnection();conn.setRequestMethod("POST");conn.setConnectTimeout(10000);conn.setReadTimeout(15000);conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
   byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream os=conn.getOutputStream()){os.write(bytes);}
   int code=conn.getResponseCode();String text=read(code>=200&&code<400?conn.getInputStream():conn.getErrorStream());
   if(code>=500||code==408||code==429)throw new OutboxSender.RetryableException("HTTP "+code);
   if(code<200||code>=300)throw new OutboxSender.PermanentException("HTTP "+code+": "+trim(text));
   JSONObject response=new JSONObject(text);long revision=response.optLong("revision",0);boolean duplicate=response.optBoolean("duplicate",false);if(revision<1)throw new OutboxSender.RetryableException("رد endpoint بلا revision صالح");return new OutboxSender.Result(revision,duplicate);
  } catch(OutboxSender.RetryableException|OutboxSender.PermanentException e){throw e;}
  catch(Exception e){throw new OutboxSender.RetryableException("تعذر الاتصال بالـendpoint التجريبي: "+e.getClass().getSimpleName());}
  finally{if(conn!=null)conn.disconnect();}
 }
 private static String read(InputStream in)throws Exception{if(in==null)return "";StringBuilder s=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)s.append(line);}return s.toString();}
 private static String trim(String s){return s==null?"":s.substring(0,Math.min(240,s.length()));}
}
