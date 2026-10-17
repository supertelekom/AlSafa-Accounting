package com.alsafa.native5.accounting;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Upload-only Firestore transport. Must run on a background thread.
 * The Firestore document ID is the immutable operation ID. Duplicate retries
 * compare the exact payload digest; conflicting data is never overwritten.
 * Requires authenticated, authorized tablet write access in Firestore rules.
 */
public final class FirestoreOperationWriter implements UploadOnlySync.CloudWriter {
    private final FirebaseFirestore firestore;
    private final String storeId;

    public FirestoreOperationWriter(FirebaseFirestore firestore,String storeId) {
        this.firestore=Objects.requireNonNull(firestore,"firestore");
        if(storeId==null || !storeId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid store ID");
        this.storeId=storeId;
    }

    @Override public long writeOnce(String operationId,String kind,String payload) throws Exception {
        if(operationId==null || !operationId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid operation ID");
        if(kind==null || kind.trim().isEmpty()) throw new IllegalArgumentException("Missing kind");
        Objects.requireNonNull(payload,"payload");
        final String digest=sha256(kind+"\n"+payload);
        final DocumentReference ref=firestore.collection("native5_stores")
            .document(storeId).collection("operations").document(operationId);
        return Tasks.await(firestore.runTransaction(transaction -> {
            DocumentSnapshot existing=transaction.get(ref);
            if(existing.exists()) {
                if(!digest.equals(existing.getString("sha256")) ||
                    !kind.equals(existing.getString("kind")))
                    throw new IllegalStateException("Cloud operation ID conflict: "+operationId);
                return 1L;
            }
            Map<String,Object> data=new HashMap<>();
            data.put("operationId",operationId);
            data.put("kind",kind);
            data.put("payload",payload);
            data.put("sha256",digest);
            data.put("revision",1L);
            data.put("createdAt",com.google.firebase.firestore.FieldValue.serverTimestamp());
            transaction.set(ref,data);
            return 1L;
        }),30,TimeUnit.SECONDS);
    }

    private static String sha256(String text) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb=new StringBuilder(bytes.length*2);
            for(byte b:bytes) sb.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            return sb.toString();
        } catch(NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
