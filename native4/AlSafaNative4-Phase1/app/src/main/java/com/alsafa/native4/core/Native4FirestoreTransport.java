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
   long revision=Tasks.await(firestore.runTransaction(tx->{
    com.google.firebase.firestore.DocumentReference opRef=firestore.collection(COLLECTION).document(op.id);
    DocumentSnapshot existing=tx.get(opRef);
    if(existing.exists()){Long r=existing.getLong("revision");if(r==null||r<=0)throw new IllegalStateException("عملية مزامنة موجودة بلا revision صالح");if(!op.id.equals(existing.getString("operationId"))||!op.kind.equals(existing.getString("kind"))||!op.recordId.equals(existing.getString("recordId"))||!op.payload.equals(existing.getString("payload"))||!op.deviceId.equals(existing.getString("sourceDeviceId")))throw new IllegalStateException("تعارض خطير: معرّف العملية موجود بمحتوى مختلف؛ لم يتم اعتماد الرفع");return r;}
    com.google.firebase.firestore.DocumentReference seqRef=firestore.collection("native4_meta").document("sequence");
    DocumentSnapshot seq=tx.get(seqRef);Long current=seq.getLong("revision");long next=(current==null?0:current)+1;
    Map<String,Object> seqData=new HashMap<>();seqData.put("revision",next);
    Map<String,Object> data=new HashMap<>();data.put("operationId",op.id);data.put("kind",op.kind);data.put("recordId",op.recordId);data.put("payload",op.payload);data.put("sourceDeviceId",op.deviceId);data.put("createdAt",System.currentTimeMillis());data.put("revision",next);
    tx.set(seqRef,seqData);tx.set(opRef,data);return next;
   }),20,TimeUnit.SECONDS);
   return new Native4SyncEngine.Ack(revision);
  }catch(Exception e){throw retryable(e);}
 }
 @Override public Native4SyncReceiver.Batch fetchAfter(long revision,int limit)throws Exception{
  QuerySnapshot snap=Tasks.await(firestore.collection(COLLECTION).whereGreaterThan("revision",revision).orderBy("revision",Query.Direction.ASCENDING).limit(Math.max(1,Math.min(limit,100))).get(),20,TimeUnit.SECONDS);
  List<Native4SyncReceiver.RemoteOp> out=new ArrayList<>();for(DocumentSnapshot d:snap.getDocuments()){Long r=d.getLong("revision");if(r==null)continue;out.add(new Native4SyncReceiver.RemoteOp(r,d.getString("operationId"),d.getString("kind"),d.getString("recordId"),d.getString("payload"),d.getString("sourceDeviceId")));}return new Native4SyncReceiver.Batch(out);
 }
 private static Native4SyncEngine.Retryable retryable(Exception e){String m=e.getMessage();return new Native4SyncEngine.Retryable(m==null?"تعذر الاتصال بـ Firestore":m);}
}
