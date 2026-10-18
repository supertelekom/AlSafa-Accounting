package com.alsafa.native5.tablet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Read-only inspection of real v3.9.359 JSON backups; no database writes. */
public final class LegacyJsonBackupPreflight {
    private static final int MAX_BYTES = 100 * 1024 * 1024;
    private static final String[] COLLECTIONS = {
        "products","maintenanceParts","sales","repairs","expenses","payments",
        "partnerWithdrawals","cashTransactions","dailyClosings","purchases",
        "suppliers","supplierLedger","returns","usedWatchTransactions","customerOrders","auditLog",
        "discountRules","loyaltyLedger","productCostHistory","smartReceipts",
        "productDetailOptions","inventoryMovements","inventorySessions",
        "customerSessions","shiftHandovers"
    };
    private LegacyJsonBackupPreflight() {}

    public static final class Inspection {
        public final String sha256;
        public final String format;
        public final int version;
        public final Map<String,Integer> counts;
        public final int bytes;
        Inspection(String sha256,String format,int version,Map<String,Integer> counts,int bytes) {
            this.sha256=sha256;this.format=format;this.version=version;
            this.counts=java.util.Collections.unmodifiableMap(new LinkedHashMap<>(counts));
            this.bytes=bytes;
        }
    }

    public static Inspection inspect(InputStream input) throws IOException, JSONException {
        if(input==null) throw new IOException("Missing backup");
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        byte[] block=new byte[8192];
        int read;
        while((read=input.read(block))!=-1) {
            if(buffer.size()>MAX_BYTES-read) throw new IOException("Backup JSON too large");
            buffer.write(block,0,read);
        }
        byte[] raw=buffer.toByteArray();
        String json=new String(raw,StandardCharsets.UTF_8);
        if(json.startsWith("\uFEFF")) json=json.substring(1);
        JSONObject root=new JSONObject(json);
        boolean envelope=root.has("format");
        JSONObject data;
        String format;
        int version;
        if(envelope) {
            if(!"alsafa-accounting-backup".equals(root.optString("format")))
                throw new JSONException("Unknown backup format");
            version=root.optInt("formatVersion",-1);
            if(version!=2) throw new JSONException("Unsupported backup format version");
            data=root.optJSONObject("data");
            format="alsafa-accounting-backup";
        } else {
            data=root;
            version=0;
            format="legacy-data-object";
        }
        if(data==null || !(data.opt("settings") instanceof JSONObject) ||
            !(data.opt("sales") instanceof JSONArray) ||
            !(data.opt("products") instanceof JSONArray))
            throw new JSONException("Required settings, sales or products missing");
        Map<String,Integer> counts=new LinkedHashMap<>();
        for(String name:COLLECTIONS) {
            Object value=data.opt(name);
            if(value!=null && !(value instanceof JSONArray))
                throw new JSONException("Invalid collection: "+name);
            if(value instanceof JSONArray) {
                JSONArray rows=(JSONArray)value;
                Set<String> seen=new HashSet<>();
                for(int i=0;i<rows.length();i++) {
                    JSONObject record=rows.optJSONObject(i);
                    if(record==null)
                        throw new JSONException("Non-object record in "+name+" at "+i);
                    String id=record.optString("id","").trim();
                    if(id.isEmpty())
                        throw new JSONException("Missing record ID in "+name+" at "+i);
                    if(!seen.add(id))
                        throw new JSONException("Duplicate record ID in "+name+" at "+i);
                }
            }
            counts.put(name,value==null?-1:((JSONArray)value).length());
        }
        MessageDigest digest;
        try {digest=MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException e) {throw new AssertionError(e);}
        StringBuilder hex=new StringBuilder(64);
        for(byte part:digest.digest(raw))
            hex.append(Character.forDigit((part>>>4)&15,16))
                .append(Character.forDigit(part&15,16));
        return new Inspection(hex.toString(),format,version,counts,raw.length);
    }
}
