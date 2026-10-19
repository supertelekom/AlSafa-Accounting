package com.alsafa.native5.tablet;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
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
     * Validates an individual sale line against its exact stock-bearing color.
     * This is a preflight check only, not a posting or a stock mutation.
     * The final SQLite posting must recheck stock inside the same transaction.
     */
    public static double requireSaleQuantity(JSONArray products, String productId, double quantity) {
        if(Double.isNaN(quantity) || Double.isInfinite(quantity) || quantity<=0)
            throw new IllegalArgumentException("Sale quantity must be finite and positive");
        double available=stockOf(products,productId);
        if(available<quantity)
            throw new IllegalStateException("Insufficient stock for product: "+productId);
        return available-quantity;
    }

    /**
     * Validates a whole invoice before posting. Repeated lines for the same
     * color are summed so two individually valid lines cannot oversell it.
     * The posting transaction must repeat this check against current SQLite stock.
     */
    public static Map<String,Double> requireInvoiceQuantities(
            JSONArray products, JSONArray lines) {
        if(lines==null || lines.length()==0)
            throw new IllegalArgumentException("Invoice has no items");
        Map<String,Double> requested=new HashMap<>();
        for(int i=0;i<lines.length();i++) {
            JSONObject line=lines.optJSONObject(i);
            if(line==null) throw new IllegalArgumentException("Invalid invoice line");
            String id=line.optString("productId","").trim();
            if(id.isEmpty()) throw new IllegalArgumentException("Missing product ID in invoice");
            Object raw=line.opt("qty");
            if(!(raw instanceof Number))
                throw new IllegalArgumentException("Missing numeric sale quantity");
            double qty=((Number)raw).doubleValue();
            if(!Double.isFinite(qty) || qty<=0)
                throw new IllegalArgumentException("Invalid sale quantity");
            double sum=requested.containsKey(id)?requested.get(id)+qty:qty;
            if(!Double.isFinite(sum))
                throw new IllegalArgumentException("Invoice quantity overflow");
            requested.put(id,sum);
        }
        Map<String,Double> remaining=new HashMap<>();
        for(Map.Entry<String,Double> entry:requested.entrySet())
            remaining.put(entry.getKey(),
                    requireSaleQuantity(products,entry.getKey(),entry.getValue()));
        return java.util.Collections.unmodifiableMap(remaining);
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
