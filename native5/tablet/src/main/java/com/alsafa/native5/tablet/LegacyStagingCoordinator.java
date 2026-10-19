package com.alsafa.native5.tablet;

import android.content.Context;
import android.net.Uri;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Explicitly staged history, never a production accounting import. */
public final class LegacyStagingCoordinator {
    private static final int MAX_BYTES=100*1024*1024;
    private LegacyStagingCoordinator() {}

    /**
     * Reopens the chosen file, validates its digest against the inspected snapshot,
     * then stages in a separate SQLite database. Caller must run off UI thread.
     */
    public static Map<String,Integer> stageInspected(
            Context context,Uri file,LegacyJsonBackupPreflight.Inspection inspected)
            throws Exception {
        if(context==null || file==null || inspected==null)
            throw new IllegalArgumentException("Missing staged backup arguments");
        byte[] raw;
        try(InputStream input=context.getContentResolver().openInputStream(file)) {
            if(input==null) throw new IOException("Backup cannot be opened");
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            byte[] block=new byte[8192];
            int n;
            while((n=input.read(block))!=-1) {
                if(buffer.size()>MAX_BYTES-n) throw new IOException("Backup too large");
                buffer.write(block,0,n);
            }
            raw=buffer.toByteArray();
        }
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        StringBuilder hash=new StringBuilder(64);
        for(byte b:digest.digest(raw))
            hash.append(Character.forDigit((b>>>4)&15,16))
                .append(Character.forDigit(b&15,16));
        if(raw.length!=inspected.bytes || !hash.toString().equals(inspected.sha256))
            throw new IOException("Backup changed since inspection; inspect again");
        String json=new String(raw,StandardCharsets.UTF_8);
        if(json.startsWith("\uFEFF")) json=json.substring(1);
        JSONObject root=new JSONObject(json);
        JSONObject data;
        if("alsafa-accounting-backup".equals(inspected.format)) {
            if(!"alsafa-accounting-backup".equals(root.optString("format")) ||
                    root.optInt("formatVersion",-1)!=2)
                throw new IOException("Backup envelope changed");
            data=root.getJSONObject("data");
        } else {
            if(root.has("format")) throw new IOException("Unexpected backup envelope");
            data=root;
        }
        // The selected bytes are digest-locked to a successful full preflight.
        // Do not parse the same large file a second time into memory.
        try(LegacyIsolatedStaging staging=new LegacyIsolatedStaging(context)) {
            Map<String,Integer> counts=staging.stage(inspected.sha256,data);
            for(Map.Entry<String,Integer> entry:inspected.counts.entrySet()) {
                int expectedCount=entry.getValue()<0?0:entry.getValue();
                Integer actual=counts.get(entry.getKey());
                if(actual==null || actual!=expectedCount)
                    throw new IllegalStateException("Staging collection count mismatch: "+entry.getKey());
            }
            Map<String,Integer> persisted=staging.verifiedCollectionCounts(inspected.sha256);
            if(!persisted.equals(counts))
                throw new IllegalStateException("Persisted collection counts do not match staged data");
            long verified=staging.verifiedRecordCount(inspected.sha256);
            long expected=0;
            for(Integer count:counts.values()) expected+=count;
            if(verified!=expected)
                throw new IllegalStateException("Staging verification failed");
            return counts;
        }
    }
}
