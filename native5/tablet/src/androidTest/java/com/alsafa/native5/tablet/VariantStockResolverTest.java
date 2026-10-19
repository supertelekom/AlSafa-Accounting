package com.alsafa.native5.tablet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VariantStockResolverTest {
    private JSONArray colors() throws Exception {
        return new JSONArray()
            .put(new JSONObject().put("id","base").put("variantGroupId","model")
                .put("stock",0))
            .put(new JSONObject().put("id","blue").put("variantGroupId","model")
                .put("stock",1))
            .put(new JSONObject().put("id","red").put("variantGroupId","model")
                .put("stock",2));
    }

    @Test public void independentColorsDoNotInheritBaseStock() throws Exception {
        JSONArray products=colors();
        assertEquals(0,VariantStockResolver.stockOf(products,"base"),0);
        assertEquals(1,VariantStockResolver.stockOf(products,"blue"),0);
        assertEquals(2,VariantStockResolver.stockOf(products,"red"),0);
        assertEquals(3,VariantStockResolver.displayGroupTotal(products,"model"),0);
        assertEquals(1,products.getJSONObject(1).getInt("stock"));
    }

    @Test public void missingVariantIdCannotFallBackToBase() throws Exception {
        try { VariantStockResolver.stockOf(colors(),"deleted-blue"); fail("Must reject"); }
        catch(IllegalArgumentException expected) { }
    }

    @Test public void duplicateVariantIdCannotSelectArbitraryStock() throws Exception {
        JSONArray products=colors().put(new JSONObject().put("id","blue")
            .put("variantGroupId","model").put("stock",100));
        try { VariantStockResolver.stockOf(products,"blue"); fail("Must reject"); }
        catch(IllegalStateException expected) { }
        try { VariantStockResolver.displayGroupTotal(products,"model"); fail("Must reject"); }
        catch(IllegalStateException expected) { }
    }
}
