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
import java.math.BigDecimal;

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
        public final Map<String,Integer> referenceIssues;
        public final Map<String,String> vaultTotals;
        public final Map<String,Integer> fractionalAmounts;
        public final Map<String,Integer> inventorySupplierIssues;
        Inspection(String sha256,String format,int version,Map<String,Integer> counts,int bytes,Map<String,Integer> referenceIssues,Map<String,String> vaultTotals,Map<String,Integer> fractionalAmounts,Map<String,Integer> inventorySupplierIssues) {
            this.sha256=sha256;this.format=format;this.version=version;
            this.counts=java.util.Collections.unmodifiableMap(new LinkedHashMap<>(counts));
            this.bytes=bytes;
            this.referenceIssues=java.util.Collections.unmodifiableMap(new LinkedHashMap<>(referenceIssues));
            this.vaultTotals=java.util.Collections.unmodifiableMap(new LinkedHashMap<>(vaultTotals));
            this.fractionalAmounts=java.util.Collections.unmodifiableMap(new LinkedHashMap<>(fractionalAmounts));
            this.inventorySupplierIssues=java.util.Collections.unmodifiableMap(new LinkedHashMap<>(inventorySupplierIssues));
        }
    }

    private static Set<String> recordIds(JSONArray records) {
        Set<String> ids=new HashSet<>();
        if(records!=null) for(int i=0;i<records.length();i++)
            ids.add(records.optJSONObject(i).optString("id",""));
        return ids;
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
        Map<String,Integer> referenceIssues=new LinkedHashMap<>();
        String[][] mappings={
            {"sale","sales"},{"repair","repairs"},{"purchase","purchases"},
            {"supplier","suppliers"},{"expense","expenses"},{"return","returns"},
            {"partnerWithdrawal","partnerWithdrawals"},{"dailyClosing","dailyClosings"}
        };
        JSONArray cash=data.optJSONArray("cashTransactions");
        if(cash!=null) for(String[] mapping:mappings) {
            JSONArray source=data.optJSONArray(mapping[1]);
            if(source==null) {
                referenceIssues.put(mapping[0]+":missing-collection",-1);
                continue;
            }
            Set<String> ids=new HashSet<>();
            for(int i=0;i<source.length();i++)
                ids.add(source.optJSONObject(i).optString("id"));
            int linked=0,orphan=0;
            for(int i=0;i<cash.length();i++) {
                JSONObject tx=cash.optJSONObject(i);
                if(!mapping[0].equals(tx.optString("referenceType"))) continue;
                linked++;
                if(!ids.contains(tx.optString("referenceId"))) orphan++;
            }
            referenceIssues.put(mapping[0]+":linked",linked);
            referenceIssues.put(mapping[0]+":orphan",orphan);
        }
        Map<String,BigDecimal> totals=new LinkedHashMap<>();
        Map<String,Integer> fractionalAmounts=new LinkedHashMap<>();
        if(cash!=null) for(int i=0;i<cash.length();i++) {
            JSONObject tx=cash.optJSONObject(i);
            String vault=tx.optString("vault","").trim();
            String currency=tx.optString("currency","").trim();
            if(vault.isEmpty() || currency.isEmpty())
                throw new JSONException("Cash transaction missing vault/currency at "+i);
            Object rawAmount=tx.opt("amount");
            if(!(rawAmount instanceof Number))
                throw new JSONException("Invalid cash amount at "+i);
            BigDecimal amount;
            try {amount=new BigDecimal(rawAmount.toString());}
            catch(NumberFormatException e) {throw new JSONException("Invalid cash amount at "+i);}
            String key=vault+" / "+currency;
            BigDecimal previous=totals.get(key);
            totals.put(key,(previous==null?BigDecimal.ZERO:previous).add(amount));
            if(amount.stripTrailingZeros().scale()>2)
                fractionalAmounts.put(key,fractionalAmounts.containsKey(key)?
                    fractionalAmounts.get(key)+1:1);
        }
        Map<String,String> vaultTotals=new LinkedHashMap<>();
        for(Map.Entry<String,BigDecimal> item:totals.entrySet())
            vaultTotals.put(item.getKey(),item.getValue().toPlainString());
        Map<String,Integer> inventorySupplierIssues=new LinkedHashMap<>();
        Set<String> supplierIds=recordIds(data.optJSONArray("suppliers"));
        int missingSuppliers=0;
        for(String collection:new String[]{"purchases","supplierLedger"}) {
            JSONArray records=data.optJSONArray(collection);
            if(records==null) continue;
            for(int i=0;i<records.length();i++) {
                String supplierId=records.optJSONObject(i).optString("supplierId","");
                if(!supplierId.isEmpty() && !supplierIds.contains(supplierId))
                    missingSuppliers++;
            }
        }
        inventorySupplierIssues.put("missingSupplierReferences",missingSuppliers);
        Set<String> productIds=recordIds(data.optJSONArray("products"));
        Set<String> partIds=recordIds(data.optJSONArray("maintenanceParts"));
        int missingInventory=0,unknownItemType=0;
        JSONArray movements=data.optJSONArray("inventoryMovements");
        if(movements!=null) for(int i=0;i<movements.length();i++) {
            JSONObject movement=movements.optJSONObject(i);
            String type=movement.optString("itemType","");
            String itemId=movement.optString("itemId","");
            if("product".equals(type)) {
                if(!productIds.contains(itemId)) missingInventory++;
            } else if("part".equals(type)) {
                if(!partIds.contains(itemId)) missingInventory++;
            } else unknownItemType++;
        }
        inventorySupplierIssues.put("missingInventoryItemReferences",missingInventory);
        inventorySupplierIssues.put("unknownInventoryItemTypes",unknownItemType);
        MessageDigest digest;
        try {digest=MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException e) {throw new AssertionError(e);}
        StringBuilder hex=new StringBuilder(64);
        for(byte part:digest.digest(raw))
            hex.append(Character.forDigit((part>>>4)&15,16))
                .append(Character.forDigit(part&15,16));
        return new Inspection(hex.toString(),format,version,counts,raw.length,referenceIssues,vaultTotals,fractionalAmounts,inventorySupplierIssues);
    }
}
