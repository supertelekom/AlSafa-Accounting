package com.alsafa.native5.tablet;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only audit of legacy color variants. Never repairs quantities, changes
 * ownership of a variant, or guesses which record is the base product.
 */
public final class VariantInventoryAudit {
    private VariantInventoryAudit() {}

    public static final class Findings {
        public int groups;
        public int multiColorGroups;
        public int missingBaseGroups;
        public int multipleBaseGroups;
        public int conflictingBaseNames;
        public int duplicateBarcodeGroups;
        public int negativeStockRecords;
        public int salesWithMissingProduct;
        public int salesWithGroupedProduct;
        public int variantSalesWithoutOwnProductId;
        public int groupsWithMixedStock;
        public int groupsWithBlankVariantRole;
    }

    public static Findings inspect(JSONObject data) {
        Findings findings=new Findings();
        JSONArray products=data.optJSONArray("products");
        JSONArray sales=data.optJSONArray("sales");
        if(products==null) throw new IllegalArgumentException("Missing products");
        Map<String,List<JSONObject>> groups=new HashMap<>();
        Set<String> ids=new HashSet<>();
        for(int i=0;i<products.length();i++) {
            JSONObject product=products.optJSONObject(i);
            if(product==null) throw new IllegalArgumentException("Invalid product");
            String id=product.optString("id","").trim();
            if(id.isEmpty() || !ids.add(id))
                throw new IllegalArgumentException("Missing or duplicate product ID");
            if(product.optDouble("stock",0)<0) findings.negativeStockRecords++;
            String group=product.optString("variantGroupId","").trim();
            if(!group.isEmpty()) {
                if(!groups.containsKey(group)) groups.put(group,new ArrayList<>());
                groups.get(group).add(product);
            }
        }
        findings.groups=groups.size();
        Set<String> groupedIds=new HashSet<>();
        for(List<JSONObject> members:groups.values()) {
            if(members.size()<2) continue;
            findings.multiColorGroups++;
            int bases=0;
            boolean hasZeroStock=false;
            boolean hasPositiveStock=false;
            boolean hasBlankRole=false;
            Set<String> baseNames=new HashSet<>();
            Set<String> barcodes=new HashSet<>();
            boolean duplicateBarcode=false;
            for(JSONObject member:members) {
                groupedIds.add(member.optString("id"));
                if(member.optDouble("stock",0)==0) hasZeroStock=true;
                if(member.optDouble("stock",0)>0) hasPositiveStock=true;
                if(member.optString("variantRole","").trim().isEmpty()) hasBlankRole=true;
                if("base".equals(member.optString("variantRole"))) bases++;
                String name=member.optString("variantBaseName","").trim();
                if(!name.isEmpty()) baseNames.add(name);
                String barcode=member.optString("barcode","").trim();
                if(!barcode.isEmpty() && !barcodes.add(barcode)) duplicateBarcode=true;
            }
            if(hasZeroStock && hasPositiveStock) findings.groupsWithMixedStock++;
            if(hasBlankRole) findings.groupsWithBlankVariantRole++;
            if(bases==0) findings.missingBaseGroups++;
            if(bases>1) findings.multipleBaseGroups++;
            if(baseNames.size()>1) findings.conflictingBaseNames++;
            if(duplicateBarcode) findings.duplicateBarcodeGroups++;
        }
        if(sales!=null) for(int i=0;i<sales.length();i++) {
            JSONObject sale=sales.optJSONObject(i);
            if(sale==null) continue;
            JSONArray items=sale.optJSONArray("items");
            if(items==null) continue;
            for(int j=0;j<items.length();j++) {
                JSONObject item=items.optJSONObject(j);
                if(item==null) continue;
                String productId=item.optString("productId","").trim();
                if(productId.isEmpty()) {
                    findings.variantSalesWithoutOwnProductId++;
                    continue;
                }
                if(!ids.contains(productId)) findings.salesWithMissingProduct++;
                if(groupedIds.contains(productId)) findings.salesWithGroupedProduct++;
            }
        }
        return findings;
    }
}
