package com.alsafa.native4.core;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Direct Firestore transport for Native4 operation log. No snapshots and no database replacement. */
public final class Native4FirestoreTransport implements Native4SyncEngine.Transport, Native4SyncReceiver.Source {
 private static final String COLLECTION="native4_operations";
 private final FirebaseFirestore firestore;
 public Native4FirestoreTransport(){this(FirebaseFirestore.getInstance());}
 Native4FirestoreTransport(FirebaseFirestore firestore){this.firestore=firestore;}
 @Override public Native4SyncEngine.Ack push(Native4SyncEngine.Op op)throws Native4SyncEngine.Retryable,Native4SyncEngine.Permanent{
  try{
   Map<String,Object> data=new HashMap<>();data.put("operationId",op.id);data.put("kind",op.kind);data.put("recordId",op.recordId);data.put("payload",op.payload);data.put("sourceDeviceId",op.deviceId);data.put("createdAt",System.currentTimeMillis());
   DocumentSnapshot existing=Tasks.await(firestore.collection(COLLECTION).document(op.id).get(),15,TimeUnit.SECONDS);
   if(existing.exists()){Long revision=existing.getLong("revision");return new Native4SyncEngine.Ack(revision==null?0:revision);}
   long revision=nextRevision();data.put("revision",revision);
   Tasks.await(firestore.collection(COLLECTION).document(op.id).set(data),15,TimeUnit.SECONDS);
   return new Native4SyncEngine.Ack(revision);
  }catch(Exception e){throw retryable(e);}
 }
 @Override public Native4SyncReceiver.Batch fetchAfter(long revision,int limit)throws Exception{
  QuerySnapshot snap=Tasks.await(firestore.collection(COLLECTION).whereGreaterThan("revision",revision).orderBy("revision",Query.Direction.ASCENDING).limit(Math.max(1,Math.min(limit,100))).get(),20,TimeUnit.SECONDS);
  List<Native4SyncReceiver.RemoteOp> out=new ArrayList<>();for(DocumentSnapshot d:snap.getDocuments()){Long r=d.getLong("revision");if(r==null)continue;out.add(new Native4SyncReceiver.RemoteOp(r,d.getString("operationId"),d.getString("kind"),d.getString("recordId"),d.getString("payload"),d.getString("sourceDeviceId")));}return new Native4SyncReceiver.Batch(out);
 }
 private long nextRevision()throws Exception{
  return Tasks.await(firestore.runTransaction(tx->{DocumentSnapshot s=tx.get(firestore.collection("native4_meta").document("sequence"));Long current=s.getLong("revision");long next=(current==null?0:current)+1;Map<String,Object> v=new HashMap<>();v.put("revision",next);tx.set(firestore.collection("native4_meta").document("sequence"),v);return next;}),15,TimeUnit.SECONDS);
 }
 private static Native4SyncEngine.Retryable retryable(Exception e){String m=e.getMessage();return new Native4SyncEngine.Retryable(m==null?"تعذر الاتصال بـ Firestore":m);}
}
