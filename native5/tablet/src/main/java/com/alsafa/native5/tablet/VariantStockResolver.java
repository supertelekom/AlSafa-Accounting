package com.alsafa.native5.tablet;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/**
 * A stock-bearing color is always an individual product record.
 * Group IDs are presentation metadata and never a stock account.
 * Pure read-only helper: does not mutate products, sales or inventory movements.
 */
public final class VariantStockResolver {
    private VariantStockResolver() {}

    public static JSONObject requireProduct(JSONArray products, String productId) {
        if(products==null || productId==null || productId.trim().isEmpty())
            throw new IllegalArgumentException("Explicit product ID is required");
        JSONObject match=null;
        for(int i=0;i<products.length();i++) {
            JSONObject product=products.optJSONObject(i);
            if(product==null) throw new IllegalArgumentException("Invalid product record");
            if(productId.equals(product.optString("id"))) {
                if(match!=null) throw new IllegalStateException("Duplicate product ID: "+productId);
                match=product;
            }
        }
        if(match==null) throw new IllegalArgumentException("Unknown product ID: "+productId);
        return match;
    }

    public static double stockOf(JSONArray products, String productId) {
        JSONObject product=requireProduct(products,productId);
        Object raw=product.opt("stock");
        if(!(raw instanceof Number))
            throw new IllegalStateException("Missing or nonnumeric stock for product: "+productId);
        double stock=((Number)raw).doubleValue();
        if(Double.isNaN(stock) || Double.isInfinite(stock))
            throw new IllegalStateException("Invalid stock for product: "+productId);
        return stock;
    }

    /**
     * Informational group total, never used to authorize a sale or decrement stock.
     * Excludes ungrouped products; deduplicates by product ID.
     */
    public static double displayGroupTotal(JSONArray products, String groupId) {
        if(products==null || groupId==null || groupId.trim().isEmpty())
            throw new IllegalArgumentException("Explicit group ID is required");
        double total=0;
        Set<String> seen=new HashSet<>();
        for(int i=0;i<products.length();i++) {
            JSONObject p=products.optJSONObject(i);
            if(p==null) throw new IllegalArgumentException("Invalid product record");
            if(!groupId.equals(p.optString("variantGroupId"))) continue;
            String id=p.optString("id","").trim();
            if(id.isEmpty() || !seen.add(id))
                throw new IllegalStateException("Missing or duplicate grouped product ID");
            Object raw=p.opt("stock");
            if(!(raw instanceof Number))
                throw new IllegalStateException("Missing or nonnumeric stock for product: "+id);
            double qty=((Number)raw).doubleValue();
            if(Double.isNaN(qty) || Double.isInfinite(qty))
                throw new IllegalStateException("Invalid stock for product: "+id);
            total+=qty;
        }
        return total;
    }
}
