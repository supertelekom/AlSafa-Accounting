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
        assertEquals(1,findings.groupsWithMixedStock);
        assertEquals(0,findings.groupsWithBlankVariantRole);
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
        assertEquals(1,findings.groupsWithBlankVariantRole);
    }
    @Test public void missingSaleProductIdIsReportedWithoutGuessingVariant() throws Exception {
        JSONObject data=new JSONObject().put("products",new JSONArray()
            .put(new JSONObject().put("id","color-blue").put("stock",1)
                .put("variantGroupId","model-x").put("variantRole","additional"))
            .put(new JSONObject().put("id","color-black").put("stock",0)
                .put("variantGroupId","model-x").put("variantRole","base")))
            .put("sales",new JSONArray().put(new JSONObject().put("items",
                new JSONArray().put(new JSONObject().put("name","Model X blue")
                    .put("qty",1)))));
        VariantInventoryAudit.Findings findings=VariantInventoryAudit.inspect(data);
        assertEquals(1,findings.saleLinesMissingProductId);
        assertEquals(0,findings.salesWithGroupedProduct);
        assertEquals(1,findings.groupsWithMixedStock);
    }

    @Test public void duplicateBarcodeAcrossDifferentGroupsIsDetected() throws Exception {
        JSONObject data=new JSONObject().put("products",new JSONArray()
            .put(new JSONObject().put("id","red").put("variantGroupId","group1")
                .put("barcode","shared"))
            .put(new JSONObject().put("id","blue").put("variantGroupId","group2")
                .put("barcode","shared")));
        VariantInventoryAudit.Findings findings=VariantInventoryAudit.inspect(data);
        assertEquals(1,findings.duplicateBarcodesAcrossProducts);
        assertEquals(0,findings.duplicateBarcodeGroups);
    }

    @Test public void saleReferenceToMissingColorIsReportedWithoutStockChange() throws Exception {
        JSONObject product=new JSONObject().put("id","available").put("stock",3);
        JSONObject data=new JSONObject().put("products",new JSONArray().put(product))
            .put("sales",new JSONArray().put(new JSONObject().put("items",
                new JSONArray().put(new JSONObject().put("productId","deleted-color")))));
        VariantInventoryAudit.Findings findings=VariantInventoryAudit.inspect(data);
        assertEquals(1,findings.saleLinesLinkedToMissingProducts);
        assertEquals(3,product.getInt("stock"));
    }

}
