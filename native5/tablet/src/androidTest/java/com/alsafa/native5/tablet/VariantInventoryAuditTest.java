package com.alsafa.native5.tablet;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic inventory data only; never customer records. */
public class VariantInventoryAuditTest {
    @Test public void independentVariantsRemainUnchangedDuringAudit() throws Exception {
        JSONObject base=new JSONObject().put("id","base").put("stock",0)
            .put("variantGroupId","group").put("variantRole","base")
            .put("variantBaseName","Model").put("barcode","B1");
        JSONObject extra=new JSONObject().put("id","extra").put("stock",1)
            .put("variantGroupId","group").put("variantRole","additional")
            .put("variantBaseName","Model").put("barcode","B2");
        JSONObject data=new JSONObject().put("products",new JSONArray().put(base).put(extra))
            .put("sales",new JSONArray().put(new JSONObject().put("items",
                new JSONArray().put(new JSONObject().put("productId","extra").put("qty",1)))));
        String before=data.toString();
        VariantInventoryAudit.Findings findings=VariantInventoryAudit.inspect(data);
        assertEquals(1,findings.groups);
        assertEquals(1,findings.multiColorGroups);
        assertEquals(0,findings.missingBaseGroups);
        assertEquals(1,findings.salesWithGroupedProduct);
        assertEquals(0,findings.duplicateBarcodeGroups);
        assertEquals(before,data.toString());
        assertEquals(0,base.getInt("stock"));
        assertEquals(1,extra.getInt("stock"));
    }

    @Test public void missingBaseAndConflictingNamesAreFlaggedNotCorrected() throws Exception {
        JSONObject data=new JSONObject().put("products",new JSONArray()
            .put(new JSONObject().put("id","a").put("variantGroupId","g")
                .put("variantBaseName","A").put("barcode","same"))
            .put(new JSONObject().put("id","b").put("variantGroupId","g")
                .put("variantBaseName","B").put("barcode","same")));
        VariantInventoryAudit.Findings findings=VariantInventoryAudit.inspect(data);
        assertEquals(1,findings.missingBaseGroups);
        assertEquals(1,findings.conflictingBaseNames);
        assertEquals(1,findings.duplicateBarcodeGroups);
    }
}
